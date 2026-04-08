package com.holderzone.hardware.camera

/**
 * Immutable SDK configuration used when creating a new [CameraController].
 */
data class CameraConfig(
    val backendPreference: CameraBackendPreference = CameraBackendPreference.AUTO,
    val lensFacing: LensFacing = LensFacing.BACK,
    val frameDeliveryConfig: FrameDeliveryConfig = FrameDeliveryConfig.DISABLED,
    val usbDeviceSelector: UsbDeviceSelector? = null,
    val enableLogging: Boolean = false,
) {
    init {
        require(
            backendPreference == CameraBackendPreference.UVC || usbDeviceSelector == null
        ) {
            "usbDeviceSelector can only be used with the UVC backend."
        }
        require(
            backendPreference == CameraBackendPreference.UVC || lensFacing != LensFacing.EXTERNAL
        ) {
            "LensFacing.EXTERNAL can only be used with the UVC backend."
        }
    }
}
