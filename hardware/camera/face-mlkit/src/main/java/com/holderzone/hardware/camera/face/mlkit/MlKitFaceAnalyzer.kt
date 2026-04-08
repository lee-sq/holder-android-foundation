package com.holderzone.hardware.camera.face.mlkit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.holderzone.hardware.camera.CameraFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Optional MLKit analyzer that transforms a [Flow] of camera frames into face detections.
 */
class MlKitFaceAnalyzer(
    options: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .enableTracking()
        .build(),
    private val minIntervalMillis: Long = 200L,
) : AutoCloseable {

    private val detector: FaceDetector = FaceDetection.getClient(options)

    /**
     * Runs MLKit face detection on the upstream frame flow.
     */
    fun analyze(frames: Flow<CameraFrame>): Flow<FaceDetectionResult> = flow {
        var lastEmissionAt = 0L
        frames.collect { frame ->
            val now = System.currentTimeMillis()
            if (now - lastEmissionAt < minIntervalMillis) {
                return@collect
            }
            val result = withContext(Dispatchers.Default) { analyzeFrame(frame) }
            if (result != null) {
                lastEmissionAt = now
                emit(result)
            }
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {
        detector.close()
    }

    private fun analyzeFrame(frame: CameraFrame): FaceDetectionResult? {
        val inputImage = InputImage.fromByteArray(
            frame.nv21,
            frame.width,
            frame.height,
            frame.rotationDegrees,
            ImageFormat.NV21,
        )
        val faces = Tasks.await(detector.process(inputImage))
        val face = faces.firstOrNull() ?: return null
        val bitmap = cropFaceBitmap(
            nv21 = frame.nv21,
            width = frame.width,
            height = frame.height,
            bounds = face.boundingBox,
            rotationDegrees = frame.rotationDegrees,
        ) ?: return null
        return FaceDetectionResult(
            bitmap = bitmap,
            bounds = face.boundingBox,
            timestampNs = frame.timestampNs,
        )
    }

    private fun cropFaceBitmap(
        nv21: ByteArray,
        width: Int,
        height: Int,
        bounds: Rect,
        rotationDegrees: Int,
    ): Bitmap? {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val output = ByteArrayOutputStream()
        if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, output)) {
            return null
        }
        val source = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size()) ?: return null
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true,
        )
        val clampedRect = Rect(
            bounds.left.coerceIn(0, rotated.width),
            bounds.top.coerceIn(0, rotated.height),
            bounds.right.coerceIn(0, rotated.width),
            bounds.bottom.coerceIn(0, rotated.height),
        )
        if (clampedRect.width() <= 0 || clampedRect.height() <= 0) {
            return null
        }
        return Bitmap.createBitmap(
            rotated,
            clampedRect.left,
            clampedRect.top,
            clampedRect.width(),
            clampedRect.height(),
        )
    }
}
