package com.holderzone.hardware.camera

/**
 * Long-lived lifecycle state exposed by [CameraController.state].
 */
sealed interface CameraState {

    data object Idle : CameraState

    data class Bound(
        val backend: CameraBackend,
        val capability: CameraCapability,
    ) : CameraState

    data class Starting(
        val backend: CameraBackend,
    ) : CameraState

    data class Previewing(
        val backend: CameraBackend,
        val capability: CameraCapability,
    ) : CameraState

    data class Stopped(
        val backend: CameraBackend,
        val capability: CameraCapability,
    ) : CameraState

    data class Error(
        val backend: CameraBackend?,
        val exception: CameraException,
    ) : CameraState

    data object Closed : CameraState
}
