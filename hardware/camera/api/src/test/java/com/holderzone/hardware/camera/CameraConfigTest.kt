package com.holderzone.hardware.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraConfigTest {

    @Test
    fun defaultConfig_keepsAutoBackendAndBackLens() {
        val config = CameraConfig()

        assertEquals(CameraBackendPreference.AUTO, config.backendPreference)
        assertEquals(LensFacing.BACK, config.lensFacing)
        assertEquals(FrameDeliveryConfig.DISABLED, config.frameDeliveryConfig)
    }

    @Test(expected = IllegalArgumentException::class)
    fun usbSelector_requiresUvcBackend() {
        CameraConfig(
            backendPreference = CameraBackendPreference.CAMERA_X,
            usbDeviceSelector = UsbDeviceSelector.ByVidPid(vendorId = 1, productId = 2),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalLens_requiresUvcBackend() {
        CameraConfig(
            backendPreference = CameraBackendPreference.CAMERA_2,
            lensFacing = LensFacing.EXTERNAL,
        )
    }
}
