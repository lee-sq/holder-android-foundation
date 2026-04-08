package com.holderzone.hardware.camera.driver.uvc

import android.content.Context
import android.hardware.usb.UsbManager
import com.holderzone.hardware.camera.CameraBackend
import com.holderzone.hardware.camera.CameraConfig
import com.holderzone.hardware.camera.internal.log.CameraLogger
import com.holderzone.hardware.camera.internal.spi.CameraDriver
import com.holderzone.hardware.camera.internal.spi.CameraDriverFactory

/**
 * Factory for the UVC backend.
 */
class UvcDriverFactory : CameraDriverFactory {
    override val backend: CameraBackend = CameraBackend.UVC

    override suspend fun isSupported(appContext: Context, config: CameraConfig): Boolean {
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.isNotEmpty()
    }

    override fun create(appContext: Context, logger: CameraLogger): CameraDriver {
        return UvcCameraDriver(appContext, logger)
    }
}
