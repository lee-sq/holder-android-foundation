package com.holderzone.hardware.camera.internal.log

/**
 * Small logging facade used by internal modules to avoid scattering raw Log calls.
 */
interface CameraLogger {
    fun debug(tag: String, message: String)

    fun warn(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

object NoopCameraLogger : CameraLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
