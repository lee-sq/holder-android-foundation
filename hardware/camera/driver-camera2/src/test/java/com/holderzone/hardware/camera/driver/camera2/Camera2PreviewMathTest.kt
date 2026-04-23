package com.holderzone.hardware.camera.driver.camera2

import com.holderzone.hardware.camera.LensFacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Camera2PreviewMathTest {

    @Test
    fun backCameraRelativeRotation_matchesOfficialFormula() {
        assertEquals(
            90,
            computeRelativeRotationDegrees(
                sensorOrientation = 90,
                displayRotationDegrees = 0,
                lensFacing = LensFacing.BACK,
            )
        )
        assertEquals(
            180,
            computeRelativeRotationDegrees(
                sensorOrientation = 90,
                displayRotationDegrees = 90,
                lensFacing = LensFacing.BACK,
            )
        )
        assertEquals(
            270,
            computeRelativeRotationDegrees(
                sensorOrientation = 90,
                displayRotationDegrees = 180,
                lensFacing = LensFacing.BACK,
            )
        )
        assertEquals(
            0,
            computeRelativeRotationDegrees(
                sensorOrientation = 90,
                displayRotationDegrees = 270,
                lensFacing = LensFacing.BACK,
            )
        )
    }

    @Test
    fun frontCameraRelativeRotation_matchesOfficialFormula() {
        assertEquals(
            270,
            computeRelativeRotationDegrees(
                sensorOrientation = 270,
                displayRotationDegrees = 0,
                lensFacing = LensFacing.FRONT,
            )
        )
        assertEquals(
            180,
            computeRelativeRotationDegrees(
                sensorOrientation = 270,
                displayRotationDegrees = 90,
                lensFacing = LensFacing.FRONT,
            )
        )
        assertEquals(
            90,
            computeRelativeRotationDegrees(
                sensorOrientation = 270,
                displayRotationDegrees = 180,
                lensFacing = LensFacing.FRONT,
            )
        )
        assertEquals(
            0,
            computeRelativeRotationDegrees(
                sensorOrientation = 270,
                displayRotationDegrees = 270,
                lensFacing = LensFacing.FRONT,
            )
        )
    }

    @Test
    fun chooseBestPreviewSize_prefersPortraitFriendlyAspectRatioWithinBounds() {
        val size = chooseBestPreviewSize(
            candidates = listOf(
                Camera2Size(width = 1920, height = 1080),
                Camera2Size(width = 1280, height = 720),
                Camera2Size(width = 960, height = 720),
                Camera2Size(width = 640, height = 480),
            ),
            viewWidth = 1080,
            viewHeight = 1920,
            sensorOrientation = 90,
            displayRotationDegrees = 0,
        )

        assertEquals(Camera2Size(width = 1280, height = 720), size)
    }

    @Test
    fun chooseBestPreviewSize_fallsBackToLargeCandidateWhenAllExceedBounds() {
        val size = chooseBestPreviewSize(
            candidates = listOf(
                Camera2Size(width = 2560, height = 1440),
                Camera2Size(width = 1920, height = 1080),
            ),
            viewWidth = 1080,
            viewHeight = 1920,
            sensorOrientation = 90,
            displayRotationDegrees = 0,
        )

        assertEquals(Camera2Size(width = 1920, height = 1080), size)
    }

    @Test
    fun previewTransformSpec_swapsMappedBufferForPortraitPreview() {
        val spec = computePreviewTransformSpec(
            previewWidth = 1280,
            previewHeight = 720,
            sensorOrientation = 90,
            displayRotationDegrees = 0,
            lensFacing = LensFacing.BACK,
        )

        assertEquals(90, spec.relativeRotationDegrees)
        assertEquals(720, spec.mappedBufferWidth)
        assertEquals(1280, spec.mappedBufferHeight)
        assertFalse(spec.mirrorHorizontally)
    }

    @Test
    fun previewTransformSpec_marksFrontPreviewAsMirrored() {
        val spec = computePreviewTransformSpec(
            previewWidth = 1280,
            previewHeight = 720,
            sensorOrientation = 270,
            displayRotationDegrees = 0,
            lensFacing = LensFacing.FRONT,
        )

        assertTrue(spec.mirrorHorizontally)
    }
}
