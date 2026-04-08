package com.holderzone.hardware.camera

import java.io.File

/**
 * Capture mode requested from [CameraController.capture].
 */
sealed interface CaptureRequest {

    /**
     * Optional destination file chosen by the caller.
     *
     * When null, the SDK writes into its default app cache directory.
     */
    val outputFile: File?

    /**
     * Uses still capture when supported and falls back to a preview snapshot otherwise.
     */
    data class PreferStill(
        override val outputFile: File? = null,
    ) : CaptureRequest

    /**
     * Requires a true still capture path from the active backend.
     */
    data class RequireStill(
        override val outputFile: File? = null,
    ) : CaptureRequest

    /**
     * Captures the current preview image instead of using the still capture pipeline.
     */
    data class PreviewSnapshot(
        override val outputFile: File? = null,
    ) : CaptureRequest
}
