package com.holderzone.hardware.camera.driver.camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import com.holderzone.hardware.camera.CameraBackend
import com.holderzone.hardware.camera.CameraConfig
import com.holderzone.hardware.camera.internal.log.CameraLogger
import com.holderzone.hardware.camera.internal.spi.CameraDriver
import com.holderzone.hardware.camera.internal.spi.CameraDriverFactory

/**
 * Factory for the Camera2 fallback backend.
 */
class Camera2DriverFactory : CameraDriverFactory {
    override val backend: CameraBackend = CameraBackend.CAMERA_2

    override suspend fun isSupported(appContext: Context, config: CameraConfig): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return hasPermission && cameraManager.cameraIdList.isNotEmpty()
    }

    override fun create(appContext: Context, logger: CameraLogger): CameraDriver {
        return Camera2CameraDriver(appContext, logger)
    }
}
