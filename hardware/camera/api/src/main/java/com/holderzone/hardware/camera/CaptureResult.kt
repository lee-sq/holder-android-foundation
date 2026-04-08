package com.holderzone.hardware.camera

/**
 * The saved output returned from [CameraController.capture].
 */
data class CaptureResult(
    val path: String,
    val kind: CaptureKind,
    val backend: CameraBackend,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Distinguishes real still capture from preview snapshots.
 */
enum class CaptureKind {
    STILL,
    SNAPSHOT,
}
