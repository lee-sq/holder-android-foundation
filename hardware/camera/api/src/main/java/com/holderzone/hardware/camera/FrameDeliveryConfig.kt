package com.holderzone.hardware.camera

/**
 * Controls preview frame delivery for downstream analysis pipelines.
 */
data class FrameDeliveryConfig(
    val enabled: Boolean = false,
    val minIntervalMillis: Long = 200L,
) {
    init {
        require(minIntervalMillis >= 0L) { "minIntervalMillis must be >= 0." }
    }

    companion object {
        val DISABLED = FrameDeliveryConfig(enabled = false)
    }
}
