package com.holderzone.hardware.camera

/**
 * Explicit USB device selection rules for the UVC backend.
 */
sealed interface UsbDeviceSelector {

    /**
     * Selects a device by vendor id and product id.
     */
    data class ByVidPid(
        val vendorId: Int,
        val productId: Int,
    ) : UsbDeviceSelector
}
