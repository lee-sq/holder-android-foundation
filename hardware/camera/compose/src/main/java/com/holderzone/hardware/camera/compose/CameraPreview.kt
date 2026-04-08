package com.holderzone.hardware.camera.compose

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.holderzone.hardware.camera.CameraController
import com.holderzone.hardware.camera.view.CameraPreviewView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Compose wrapper around [CameraPreviewView].
 *
 * The composable binds the controller to the host view and optionally starts preview.
 */
@Composable
fun CameraPreview(
    controller: CameraController,
    modifier: Modifier = Modifier,
    autoStart: Boolean = true,
) {
    var previewHost by remember { mutableStateOf<CameraPreviewView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CameraPreviewView(context).also { previewHost = it }
        },
        update = { previewHost = it }
    )

    LaunchedEffect(controller, previewHost) {
        val host = previewHost ?: return@LaunchedEffect
        controller.bind(host)
        if (autoStart) {
            host.awaitReadyForCameraStart(timeoutMillis = 5_000L)
            controller.start()
        }
    }

    DisposableEffect(controller, previewHost) {
        onDispose {
            previewHost?.releasePreview()
        }
    }
}

private suspend fun View.awaitReadyForCameraStart(
    timeoutMillis: Long,
) {
    if (isReadyForCameraStart()) {
        return
    }

    withTimeout(timeoutMillis) {
        suspendCancellableCoroutine<Unit> { continuation ->
            lateinit var attachListener: View.OnAttachStateChangeListener
            lateinit var layoutListener: View.OnLayoutChangeListener
            lateinit var preDrawListener: ViewTreeObserver.OnPreDrawListener

            fun cleanup() {
                removeOnAttachStateChangeListener(attachListener)
                removeOnLayoutChangeListener(layoutListener)
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
            }

            fun resumeIfReady() {
                if (!continuation.isActive) {
                    return
                }
                if (isReadyForCameraStart()) {
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
            preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    resumeIfReady()
                    return true
                }
            }

            addOnAttachStateChangeListener(attachListener)
            addOnLayoutChangeListener(layoutListener)
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }

            post { resumeIfReady() }
            continuation.invokeOnCancellation { cleanup() }
        }
    }
}

private fun View.isReadyForCameraStart(): Boolean {
    return isAttachedToWindow && width > 0 && height > 0
}
