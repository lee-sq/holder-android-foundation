package com.holderzone.hardware.camera.internal.spi

import android.content.Context
import com.holderzone.hardware.camera.CameraBackend
import com.holderzone.hardware.camera.CameraConfig
import com.holderzone.hardware.camera.internal.log.CameraLogger

/**
 * Internal factory used to lazily create backend drivers.
 */
interface CameraDriverFactory {
    val backend: CameraBackend

    suspend fun isSupported(appContext: Context, config: CameraConfig): Boolean

    fun create(appContext: Context, logger: CameraLogger): CameraDriver
}
