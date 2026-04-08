package com.holderzone.hardware.camera.face.mlkit

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Result produced by the optional MLKit face analysis module.
 */
data class FaceDetectionResult(
    val bitmap: Bitmap,
    val bounds: Rect,
    val timestampNs: Long,
)
