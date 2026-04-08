package com.holderzone.hardware.camera.driver.camerax

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.holderzone.hardware.camera.CameraBackend
import com.holderzone.hardware.camera.CameraConfig
import com.holderzone.hardware.camera.internal.log.CameraLogger
import com.holderzone.hardware.camera.internal.spi.CameraDriver
import com.holderzone.hardware.camera.internal.spi.CameraDriverFactory

/**
 * Factory for the CameraX backend.
 */
class CameraXDriverFactory : CameraDriverFactory {
    override val backend: CameraBackend = CameraBackend.CAMERA_X

    override suspend fun isSupported(appContext: Context, config: CameraConfig): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun create(appContext: Context, logger: CameraLogger): CameraDriver {
        return CameraXCameraDriver(appContext, logger)
    }
}
