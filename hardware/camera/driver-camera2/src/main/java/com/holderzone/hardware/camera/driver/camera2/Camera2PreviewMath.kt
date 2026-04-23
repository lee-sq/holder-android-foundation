package com.holderzone.hardware.camera.driver.camera2

import com.holderzone.hardware.camera.LensFacing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class Camera2Size(
    val width: Int,
    val height: Int,
)

internal data class Camera2ImageTransformSpec(
    val rotationDegrees: Int,
    val mirrorHorizontally: Boolean,
)

internal data class Camera2PreviewTransformSpec(
    val relativeRotationDegrees: Int,
    val displayRotationDegrees: Int,
    val mirrorHorizontally: Boolean,
    val mappedBufferWidth: Int,
    val mappedBufferHeight: Int,
)

internal fun computeImageTransformSpec(
    sensorOrientation: Int,
    displayRotationDegrees: Int,
    lensFacing: LensFacing,
): Camera2ImageTransformSpec {
    return Camera2ImageTransformSpec(
        rotationDegrees = computeRelativeRotationDegrees(
            sensorOrientation = sensorOrientation,
            displayRotationDegrees = displayRotationDegrees,
            lensFacing = lensFacing,
        ),
        mirrorHorizontally = lensFacing == LensFacing.FRONT,
    )
}

internal fun computePreviewTransformSpec(
    previewWidth: Int,
    previewHeight: Int,
    sensorOrientation: Int,
    displayRotationDegrees: Int,
    lensFacing: LensFacing,
): Camera2PreviewTransformSpec {
    val imageTransform = computeImageTransformSpec(
        sensorOrientation = sensorOrientation,
        displayRotationDegrees = displayRotationDegrees,
        lensFacing = lensFacing,
    )
    val mappedBufferWidth = if (imageTransform.rotationDegrees % 180 == 0) {
        previewWidth
    } else {
        previewHeight
    }
    val mappedBufferHeight = if (imageTransform.rotationDegrees % 180 == 0) {
        previewHeight
    } else {
        previewWidth
    }
    return Camera2PreviewTransformSpec(
        relativeRotationDegrees = imageTransform.rotationDegrees,
        displayRotationDegrees = normalizeRightAngleDegrees(displayRotationDegrees),
        mirrorHorizontally = imageTransform.mirrorHorizontally,
        mappedBufferWidth = mappedBufferWidth,
        mappedBufferHeight = mappedBufferHeight,
    )
}

internal fun chooseBestPreviewSize(
    candidates: List<Camera2Size>,
    viewWidth: Int,
    viewHeight: Int,
    sensorOrientation: Int,
    displayRotationDegrees: Int,
    maxLongEdge: Int = 1280,
    maxShortEdge: Int = 720,
): Camera2Size {
    require(candidates.isNotEmpty()) { "Preview size candidates must not be empty." }
    val boundedCandidates = candidates.filter { candidate ->
        candidate.fitsWithin(maxLongEdge = maxLongEdge, maxShortEdge = maxShortEdge)
    }
    val rankedCandidates = boundedCandidates.ifEmpty { candidates }
    val targetAspectRatio = viewWidth.toDouble() / viewHeight.toDouble()
    val shouldSwapAxes = shouldSwapDimensions(
        sensorOrientation = sensorOrientation,
        displayRotationDegrees = displayRotationDegrees,
    )
    val areaComparator = if (boundedCandidates.isNotEmpty()) {
        compareByDescending<Camera2Size> { it.width.toLong() * it.height.toLong() }
    } else {
        compareBy<Camera2Size> { it.width.toLong() * it.height.toLong() }
    }
    return rankedCandidates.minWithOrNull(
        compareBy<Camera2Size> {
            abs(it.previewAspectRatio(shouldSwapAxes) - targetAspectRatio)
        }.then(areaComparator)
    ) ?: rankedCandidates.first()
}

internal fun computeRelativeRotationDegrees(
    sensorOrientation: Int,
    displayRotationDegrees: Int,
    lensFacing: LensFacing,
): Int {
    val sign = if (lensFacing == LensFacing.FRONT) 1 else -1
    return normalizeRightAngleDegrees(sensorOrientation - displayRotationDegrees * sign)
}

internal fun shouldSwapDimensions(
    sensorOrientation: Int,
    displayRotationDegrees: Int,
): Boolean {
    return normalizeRightAngleDegrees(sensorOrientation - displayRotationDegrees) % 180 != 0
}

private fun Camera2Size.previewAspectRatio(shouldSwapAxes: Boolean): Double {
    return if (shouldSwapAxes) {
        height.toDouble() / width.toDouble()
    } else {
        width.toDouble() / height.toDouble()
    }
}

private fun Camera2Size.fitsWithin(
    maxLongEdge: Int,
    maxShortEdge: Int,
): Boolean {
    return max(width, height) <= maxLongEdge && min(width, height) <= maxShortEdge
}

private fun normalizeRightAngleDegrees(value: Int): Int {
    return ((value % 360) + 360) % 360
}
