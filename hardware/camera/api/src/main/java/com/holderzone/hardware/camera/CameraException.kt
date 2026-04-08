package com.holderzone.hardware.camera

/**
 * Unified SDK error model.
 */
sealed class CameraException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class ConfigurationException(
        message: String,
        cause: Throwable? = null,
    ) : CameraException(message, cause)

    class PermissionDeniedException(
        message: String = "Camera permission is required.",
        cause: Throwable? = null,
    ) : CameraException(message, cause)

    class DeviceUnavailableException(
        message: String,
        cause: Throwable? = null,
    ) : CameraException(message, cause)

    class DeviceSelectionRequiredException(
        message: String,
        cause: Throwable? = null,
    ) : CameraException(message, cause)

    class PreviewBindingException(
        message: String,
        cause: Throwable? = null,
    ) : CameraException(message, cause)

    class CaptureFailureException(
        message: String,
        cause: Throwable? = null,
    ) : CameraException(message, cause)

    class ClosedException(
        message: String = "CameraController is already closed.",
    ) : CameraException(message)
}
