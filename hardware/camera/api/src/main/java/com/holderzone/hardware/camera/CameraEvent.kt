package com.holderzone.hardware.camera

/**
 * One-shot events emitted by [CameraController].
 */
sealed interface CameraEvent {

    data class BackendSelected(val backend: CameraBackend) : CameraEvent

    data class PreviewStarted(val backend: CameraBackend) : CameraEvent

    data class PreviewStopped(val backend: CameraBackend) : CameraEvent

    data class CaptureCompleted(val result: CaptureResult) : CameraEvent

    data class Info(val message: String) : CameraEvent

    data class Error(val exception: CameraException) : CameraEvent
}
