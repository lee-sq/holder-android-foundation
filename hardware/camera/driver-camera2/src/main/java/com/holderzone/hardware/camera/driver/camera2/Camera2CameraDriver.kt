package com.holderzone.hardware.camera.driver.camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import com.holderzone.hardware.camera.AvailableCamera
import com.holderzone.hardware.camera.CameraBackend
import com.holderzone.hardware.camera.CameraCapability
import com.holderzone.hardware.camera.CameraConfig
import com.holderzone.hardware.camera.CameraEvent
import com.holderzone.hardware.camera.CameraException
import com.holderzone.hardware.camera.CameraFrame
import com.holderzone.hardware.camera.CaptureKind
import com.holderzone.hardware.camera.CaptureRequest as SdkCaptureRequest
import com.holderzone.hardware.camera.CaptureResult
import com.holderzone.hardware.camera.FrameDeliveryConfig
import com.holderzone.hardware.camera.LensFacing
import com.holderzone.hardware.camera.PreviewHost
import com.holderzone.hardware.camera.internal.log.CameraLogger
import com.holderzone.hardware.camera.internal.spi.CameraDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camera2 fallback driver.
 *
 * It only exposes preview snapshot capture in the first SDK version.
 */
class Camera2CameraDriver(
    private val appContext: Context,
    private val logger: CameraLogger,
) : CameraDriver {

    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val eventFlow = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 16)
    private val frameFlow = MutableSharedFlow<CameraFrame>(extraBufferCapacity = 1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    override val backend: CameraBackend = CameraBackend.CAMERA_2
    override val capabilities: CameraCapability = CameraCapability(
        switchLens = cameraManager.cameraIdList.size > 1,
        switchCamera = cameraManager.cameraIdList.size > 1,
        stillCapture = false,
        previewSnapshot = true,
        frameStreaming = true,
        uvcSelection = false,
    )
    override val frames: Flow<CameraFrame> = frameFlow.asSharedFlow()
    override val events: Flow<CameraEvent> = eventFlow.asSharedFlow()

    private var previewHost: PreviewHost? = null
    private var textureView: TextureView? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var currentConfig: CameraConfig? = null
    private var currentLensFacing: LensFacing = LensFacing.BACK
    private var selectedCameraId: String? = null
    private var activeCameraId: String? = null
    private var activePreviewSize: Size? = null
    private var activeSensorOrientation: Int? = null
    private var latestFrame: CameraFrame? = null
    private var captureWaiter: CompletableDeferred<CameraFrame>? = null
    private var lastFrameAtMs: Long = 0L
    private var isDisplayListenerRegistered = false
    private var closed = false
    private val previewLayoutChangeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
            applyPreviewTransformIfReady()
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            val previewDisplay = textureView?.display ?: return
            if (previewDisplay.displayId == displayId) {
                applyPreviewTransformIfReady()
            }
        }
    }

    override suspend fun bind(host: PreviewHost, config: CameraConfig) {
        ensureOpen()
        currentConfig = config
        currentLensFacing = config.lensFacing
        selectedCameraId = null
        previewHost = host
        textureView?.removeOnLayoutChangeListener(previewLayoutChangeListener)
        textureView = TextureView(host.previewContext).apply {
            addOnLayoutChangeListener(previewLayoutChangeListener)
        }
        host.attachPreview(textureView!!)
    }

    override suspend fun start() {
        ensureOpen()
        ensurePermission()
        ensureBackgroundThread()

        val texture = textureView ?: throw CameraException.PreviewBindingException(
            "Camera2 preview host is not bound."
        )
        awaitPreviewHostReady(texture)
        val surfaceTexture = awaitSurfaceTexture(texture)
        val cameraId = selectedCameraId ?: selectCameraId(currentLensFacing)
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val previewSize = choosePreviewSize(
            characteristics = characteristics,
            viewWidth = texture.width,
            viewHeight = texture.height,
            surfaceRotationDegrees = displayRotationDegreesOf(texture),
        )

        selectedCameraId = cameraId
        activeCameraId = cameraId
        activePreviewSize = previewSize
        activeSensorOrientation = sensorOrientation
        val device = openCamera(cameraId)
        cameraDevice = device

        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        registerDisplayListenerIfNeeded()
        applyPreviewTransformIfReady()
        val previewSurface = Surface(surfaceTexture)
        val reader = ImageReader.newInstance(
            previewSize.width,
            previewSize.height,
            ImageFormat.YUV_420_888,
            2,
        )
        imageReader = reader
        val frameConfig = currentConfig?.frameDeliveryConfig ?: FrameDeliveryConfig.DISABLED
        reader.setOnImageAvailableListener(
            { source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                handleImage(image, frameConfig)
            },
            backgroundHandler,
        )

        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            addTarget(reader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        captureSession = createCaptureSession(device, listOf(previewSurface, reader.surface)).also { session ->
            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
        }
        eventFlow.emit(CameraEvent.PreviewStarted(backend))
    }

    override suspend fun stop() {
        unregisterDisplayListenerIfNeeded()
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        activeCameraId = null
        activePreviewSize = null
        activeSensorOrientation = null
        latestFrame = null
        eventFlow.emit(CameraEvent.PreviewStopped(backend))
    }

    override suspend fun switchLens(facing: LensFacing) {
        if (!capabilities.switchLens) {
            throw CameraException.ConfigurationException("Camera2 backend does not support lens switching.")
        }
        currentLensFacing = facing
        selectedCameraId = null
        if (cameraDevice != null) {
            stop()
            start()
        }
    }

    override suspend fun switchToNextCamera() {
        ensureOpen()
        val cameras = buildAvailableCameras()
        if (cameras.size < 2) {
            throw CameraException.DeviceUnavailableException("No alternate Camera2 camera is available.")
        }
        val currentId = resolveCurrentCameraId(cameras)
        val currentIndex = cameras.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % cameras.size
        } else {
            0
        }
        val nextCamera = cameras[nextIndex]
        selectedCameraId = nextCamera.id
        nextCamera.lensFacing?.let { currentLensFacing = it }
        if (cameraDevice != null) {
            stop()
            start()
        }
    }

    override suspend fun queryAvailableCameras(): List<AvailableCamera> {
        ensureOpen()
        return buildAvailableCameras()
    }

    override suspend fun capture(request: SdkCaptureRequest): CaptureResult {
        ensureOpen()
        if (request is SdkCaptureRequest.RequireStill) {
            throw CameraException.CaptureFailureException(
                "Camera2 fallback only supports preview snapshots in SDK v1."
            )
        }

        val file = resolveOutputFile(
            requestedFile = request.outputFile,
            prefix = "camera2_snapshot",
        )
        val bitmap = textureView?.bitmap
        if (bitmap != null) {
            saveBitmap(bitmap, file)
        } else {
            val frame = latestFrame ?: awaitSnapshotFrame()
            saveNv21(frame, file)
        }
        return CaptureResult(
            path = file.absolutePath,
            kind = CaptureKind.SNAPSHOT,
            backend = backend,
        )
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true

        runCatching {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        }.onFailure {
            logger.error("Camera2CameraDriver", "Failed to close camera resources.", it)
        }

        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
        unregisterDisplayListenerIfNeeded()
        previewHost?.let { host ->
            textureView?.let(host::detachPreview)
        }
        textureView?.removeOnLayoutChangeListener(previewLayoutChangeListener)
        previewHost = null
        textureView = null
        latestFrame = null
        captureWaiter?.cancel()
        captureWaiter = null
        scope.cancel()
    }

    private fun handleImage(
        image: Image,
        frameConfig: FrameDeliveryConfig,
    ) {
        try {
            val imageTransform = currentImageTransformSpec()
            val frame = CameraFrame(
                nv21 = image.toNv21(),
                width = image.width,
                height = image.height,
                rotationDegrees = imageTransform.rotationDegrees,
                timestampNs = image.timestamp,
            )
            latestFrame = frame
            captureWaiter?.takeIf { !it.isCompleted }?.complete(frame)
            captureWaiter = null

            val now = System.currentTimeMillis()
            if (frameConfig.enabled && now - lastFrameAtMs >= frameConfig.minIntervalMillis) {
                lastFrameAtMs = now
                scope.launch {
                    frameFlow.emit(frame)
                }
            }
        } catch (throwable: Throwable) {
            scope.launch {
                eventFlow.emit(
                    CameraEvent.Error(
                        CameraException.CaptureFailureException(
                            "Camera2 frame pipeline failed.",
                            throwable,
                        )
                    )
                )
            }
        } finally {
            image.close()
        }
    }

    private suspend fun awaitSnapshotFrame(): CameraFrame {
        val waiter = CompletableDeferred<CameraFrame>()
        captureWaiter = waiter
        return waiter.await()
    }

    private fun currentImageTransformSpec(): Camera2ImageTransformSpec {
        val sensorOrientation = activeSensorOrientation
            ?: return Camera2ImageTransformSpec(
                rotationDegrees = 0,
                mirrorHorizontally = currentLensFacing == LensFacing.FRONT,
            )
        return computeImageTransformSpec(
            sensorOrientation = sensorOrientation,
            displayRotationDegrees = textureView?.let(::displayRotationDegreesOf) ?: 0,
            lensFacing = currentLensFacing,
        )
    }

    private fun ensureBackgroundThread() {
        if (backgroundThread != null) {
            return
        }
        backgroundThread = HandlerThread("Camera2Driver").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private suspend fun awaitSurfaceTexture(textureView: TextureView): SurfaceTexture {
        textureView.surfaceTexture?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    textureView.surfaceTextureListener = null
                    continuation.resume(surface)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    private suspend fun awaitPreviewHostReady(textureView: TextureView) {
        if (textureView.isReadyForCameraStart()) {
            return
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            lateinit var attachListener: View.OnAttachStateChangeListener
            lateinit var layoutListener: View.OnLayoutChangeListener
            lateinit var preDrawListener: ViewTreeObserver.OnPreDrawListener

            fun cleanup() {
                textureView.removeOnAttachStateChangeListener(attachListener)
                textureView.removeOnLayoutChangeListener(layoutListener)
                if (textureView.viewTreeObserver.isAlive) {
                    textureView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
            }

            fun resumeIfReady() {
                if (!continuation.isActive) {
                    return
                }
                if (textureView.isReadyForCameraStart()) {
                    cleanup()
                    continuation.resume(Unit)
                }
            }

            attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    resumeIfReady()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                resumeIfReady()
            }
            preDrawListener = ViewTreeObserver.OnPreDrawListener {
                resumeIfReady()
                true
            }

            textureView.addOnAttachStateChangeListener(attachListener)
            textureView.addOnLayoutChangeListener(layoutListener)
            if (textureView.viewTreeObserver.isAlive) {
                textureView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }

            textureView.post { resumeIfReady() }
            continuation.invokeOnCancellation { cleanup() }
        }
    }

    private suspend fun openCamera(cameraId: String): CameraDevice {
        return suspendCancellableCoroutine { continuation ->
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        continuation.resume(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        continuation.resumeWithException(
                            CameraException.DeviceUnavailableException("Camera2 camera disconnected.")
                        )
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        continuation.resumeWithException(
                            CameraException.DeviceUnavailableException("Camera2 open failed with code $error.")
                        )
                    }
                },
                backgroundHandler,
            )
        }
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        surfaces: List<Surface>,
    ): CameraCaptureSession {
        return suspendCancellableCoroutine { continuation ->
            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        continuation.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        continuation.resumeWithException(
                            CameraException.PreviewBindingException("Camera2 capture session configuration failed.")
                        )
                    }
                },
                backgroundHandler,
            )
        }
    }

    private fun selectCameraId(facing: LensFacing): String {
        val desired = when (facing) {
            LensFacing.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
            LensFacing.BACK -> CameraCharacteristics.LENS_FACING_BACK
            LensFacing.EXTERNAL -> CameraCharacteristics.LENS_FACING_EXTERNAL
        }
        val fallback = if (desired == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }
        return findCameraId(desired)
            ?: findCameraId(fallback)
            ?: cameraManager.cameraIdList.firstOrNull()
            ?: throw CameraException.DeviceUnavailableException("No Camera2 camera is available.")
    }

    private fun resolveCurrentCameraId(cameras: List<AvailableCamera>): String? {
        return activeCameraId
            ?: selectedCameraId
            ?: cameras.firstOrNull { it.lensFacing == currentLensFacing }?.id
            ?: cameras.firstOrNull()?.id
    }

    private fun buildAvailableCameras(): List<AvailableCamera> {
        val activeId = resolveCurrentCameraIdFromManager()
        return cameraManager.cameraIdList.mapIndexed { index, cameraId ->
            val lensFacing = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING)
                .toLensFacing()
            AvailableCamera(
                index = index,
                id = cameraId,
                displayName = buildDisplayName(
                    prefix = "Camera2",
                    index = index,
                    lensFacing = lensFacing,
                ),
                backend = backend,
                lensFacing = lensFacing,
                isActive = cameraId == activeId,
            )
        }
    }

    private fun resolveCurrentCameraIdFromManager(): String? {
        return activeCameraId
            ?: selectedCameraId
            ?: runCatching { selectCameraId(currentLensFacing) }.getOrNull()
    }

    private fun findCameraId(facing: Int): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    private fun choosePreviewSize(
        characteristics: CameraCharacteristics,
        viewWidth: Int,
        viewHeight: Int,
        surfaceRotationDegrees: Int,
    ): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw CameraException.PreviewBindingException("Camera2 stream configuration is unavailable.")
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val textureSizes = map.getOutputSizes(SurfaceTexture::class.java)
            ?.map { size -> Camera2Size(width = size.width, height = size.height) }
            .orEmpty()
        if (textureSizes.isEmpty()) {
            throw CameraException.PreviewBindingException("Camera2 preview output sizes are unavailable.")
        }
        val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            ?.map { size -> size.width to size.height }
            ?.toSet()
            .orEmpty()
        val compatibleSizes = textureSizes.filter { candidate ->
            (candidate.width to candidate.height) in yuvSizes
        }.ifEmpty { textureSizes }
        val selected = chooseBestPreviewSize(
            candidates = compatibleSizes,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            sensorOrientation = sensorOrientation,
            displayRotationDegrees = surfaceRotationDegrees,
        )
        return Size(selected.width, selected.height)
    }

    private fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CAMERA,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw CameraException.PermissionDeniedException()
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw CameraException.ClosedException()
        }
    }

    private fun resolveOutputFile(
        requestedFile: File?,
        prefix: String,
    ): File {
        requestedFile?.let { file ->
            file.parentFile?.mkdirs()
            return file
        }
        return createOutputFile(prefix)
    }

    private fun createOutputFile(prefix: String): File {
        val parent = File(appContext.cacheDir, "camera-sdk").apply { mkdirs() }
        return File(parent, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw CameraException.CaptureFailureException("Failed to compress Camera2 preview bitmap.")
            }
        }
    }

    private fun saveNv21(frame: CameraFrame, file: File) {
        val yuv = YuvImage(frame.nv21, ImageFormat.NV21, frame.width, frame.height, null)
        val output = ByteArrayOutputStream()
        if (!yuv.compressToJpeg(Rect(0, 0, frame.width, frame.height), 95, output)) {
            throw CameraException.CaptureFailureException("Failed to encode Camera2 snapshot from NV21.")
        }
        val bytes = output.toByteArray()
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw CameraException.CaptureFailureException("Failed to decode Camera2 snapshot from NV21.")
        val transformed = source.applyImageTransform(
            rotationDegrees = frame.rotationDegrees,
            mirrorHorizontally = currentLensFacing == LensFacing.FRONT,
        )
        try {
            saveBitmap(transformed, file)
        } finally {
            if (transformed !== source) {
                source.recycle()
            }
            transformed.recycle()
        }
    }

    private fun registerDisplayListenerIfNeeded() {
        if (isDisplayListenerRegistered) {
            return
        }
        displayManager.registerDisplayListener(displayListener, mainHandler)
        isDisplayListenerRegistered = true
    }

    private fun unregisterDisplayListenerIfNeeded() {
        if (!isDisplayListenerRegistered) {
            return
        }
        displayManager.unregisterDisplayListener(displayListener)
        isDisplayListenerRegistered = false
    }

    private fun applyPreviewTransformIfReady() {
        val texture = textureView ?: return
        val previewSize = activePreviewSize ?: return
        val sensorOrientation = activeSensorOrientation ?: return
        if (!texture.isReadyForCameraStart()) {
            return
        }
        val transformSpec = computePreviewTransformSpec(
            previewWidth = previewSize.width,
            previewHeight = previewSize.height,
            sensorOrientation = sensorOrientation,
            displayRotationDegrees = displayRotationDegreesOf(texture),
            lensFacing = currentLensFacing,
        )
        texture.setTransform(
            buildPreviewTransformMatrix(
                viewWidth = texture.width,
                viewHeight = texture.height,
                transformSpec = transformSpec,
            )
        )
    }

    private fun buildPreviewTransformMatrix(
        viewWidth: Int,
        viewHeight: Int,
        transformSpec: Camera2PreviewTransformSpec,
    ): Matrix {
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f,
            0f,
            transformSpec.mappedBufferWidth.toFloat(),
            transformSpec.mappedBufferHeight.toFloat(),
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        return Matrix().apply {
            setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewWidth.toFloat() / transformSpec.mappedBufferWidth.toFloat(),
                viewHeight.toFloat() / transformSpec.mappedBufferHeight.toFloat(),
            )
            postScale(scale, scale, centerX, centerY)
            postRotate(-transformSpec.displayRotationDegrees.toFloat(), centerX, centerY)
            if (transformSpec.mirrorHorizontally) {
                postScale(-1f, 1f, centerX, centerY)
            }
        }
    }
}

private fun Int?.toLensFacing(): LensFacing? {
    return when (this) {
        CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
        CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
        CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
        else -> null
    }
}

private fun buildDisplayName(
    prefix: String,
    index: Int,
    lensFacing: LensFacing?,
): String {
    val label = when (lensFacing) {
        LensFacing.FRONT -> "Front"
        LensFacing.BACK -> "Back"
        LensFacing.EXTERNAL -> "External"
        null -> "Unknown"
    }
    return "$prefix Camera ${index + 1} ($label)"
}

private fun TextureView.isReadyForCameraStart(): Boolean {
    return isAttachedToWindow && width > 0 && height > 0
}

private fun displayRotationDegreesOf(textureView: TextureView): Int {
    return when (textureView.display?.rotation ?: Surface.ROTATION_0) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
}

private fun Bitmap.applyImageTransform(
    rotationDegrees: Int,
    mirrorHorizontally: Boolean,
): Bitmap {
    if (rotationDegrees == 0 && !mirrorHorizontally) {
        return this
    }
    val matrix = Matrix().apply {
        if (rotationDegrees != 0) {
            postRotate(rotationDegrees.toFloat())
        }
        if (mirrorHorizontally) {
            postScale(-1f, 1f)
        }
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Image.toNv21(): ByteArray {
    val width = width
    val height = height
    val output = ByteArray(width * height * 3 / 2)
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBytes = ByteArray(yPlane.buffer.remaining())
    yPlane.buffer.get(yBytes)
    var offset = 0
    if (yPlane.pixelStride == 1 && yPlane.rowStride == width) {
        System.arraycopy(yBytes, 0, output, 0, yBytes.size)
        offset = yBytes.size
    } else {
        for (row in 0 until height) {
            var index = row * yPlane.rowStride
            repeat(width) {
                output[offset++] = yBytes[index]
                index += yPlane.pixelStride
            }
        }
    }

    val uBytes = ByteArray(uPlane.buffer.remaining())
    val vBytes = ByteArray(vPlane.buffer.remaining())
    uPlane.buffer.get(uBytes)
    vPlane.buffer.get(vBytes)
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        var uIndex = row * uPlane.rowStride
        var vIndex = row * vPlane.rowStride
        repeat(chromaWidth) {
            output[offset++] = vBytes[vIndex]
            output[offset++] = uBytes[uIndex]
            uIndex += uPlane.pixelStride
            vIndex += vPlane.pixelStride
        }
    }
    return output
}
