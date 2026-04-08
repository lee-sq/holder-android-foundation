package com.holderzone.hardware.camera.core

import android.util.Log
import com.holderzone.hardware.camera.internal.log.CameraLogger

/**
 * Android-backed logger used by the SDK internals.
 */
class AndroidCameraLogger(
    private val enabled: Boolean,
) : CameraLogger {
    override fun debug(tag: String, message: String) {
        if (enabled) {
            Log.d(tag, message)
        }
    }

    override fun warn(tag: String, message: String) {
        if (enabled) {
            Log.w(tag, message)
        }
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (enabled) {
            Log.e(tag, message, throwable)
        }
    }
}
