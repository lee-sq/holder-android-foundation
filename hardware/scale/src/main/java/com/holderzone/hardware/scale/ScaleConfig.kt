package com.holderzone.hardware.scale

/**
 * 一次称重会话的不可变配置。
 *
 * 对业务层来说，通常只需要关注“优先选择哪个厂商驱动”。
 * 串口默认由 SDK 按内置候选列表自动探测，只有现场机器做过特殊改线时，
 * 才需要通过 [portOverride] 手动覆盖。
 */
data class ScaleConfig(
    val vendorPreference: ScaleVendorPreference = ScaleVendorPreference.Auto,
    val portOverride: ScalePortConfig? = null,
    val vendorHints: ScaleVendorHints = ScaleVendorHints(),
    val probeTimeoutMs: Long = 1_500L,
    val logger: ScaleLogger = ScaleLogger.None,
) {
    init {
        require(probeTimeoutMs > 0) { "probeTimeoutMs must be greater than 0." }
    }
}

/**
 * 串口端点定义。
 *
 * 该模型只表示一个“明确的串口连接目标”。
 * 在常规接入场景下，推荐优先让 SDK 自动探测；只有默认候选表不适用时，
 * 才显式传入该配置作为 override。
 */
data class ScalePortConfig(
    val device: String,
    val baudRate: Int = 9_600,
) {
    init {
        require(device.isNotBlank()) { "device must not be blank." }
        require(baudRate > 0) { "baudRate must be greater than 0." }
    }
}

/**
 * 厂商特定提示项。
 *
 * 这类配置不直接暴露到底层 driver 类型，而是统一收口在 SDK 配置里。
 */
data class ScaleVendorHints(
    val jwUseSecondaryScreen: Boolean = false,
)

/**
 * 驱动厂商选择策略。
 */
sealed interface ScaleVendorPreference {

    /**
     * 使用 SDK 内置顺序自动尝试驱动。
     */
    data object Auto : ScaleVendorPreference

    /**
     * 强制限定为某个厂商实现。
     */
    data class Explicit(val vendor: ScaleVendor) : ScaleVendorPreference
}
