package com.holderzone.hardware.camera

import android.graphics.ImageFormat

/**
 * Normalized preview frame transported to analysis pipelines.
 */
data class CameraFrame(
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val format: Int = ImageFormat.NV21,
    val timestampNs: Long = System.nanoTime(),
)
