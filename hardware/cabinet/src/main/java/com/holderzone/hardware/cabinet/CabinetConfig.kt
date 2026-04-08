package com.holderzone.hardware.cabinet

/**
 * 一次留样柜会话的不可变配置。
 *
 * 该配置在 [CabinetFacade.start] 时传入，用来决定厂商选择策略、串口覆盖、
 * 柜门数量、心跳恢复策略、标定参数存储方式以及日志出口。
 */
data class CabinetConfig(
    val vendorPreference: CabinetVendorPreference = CabinetVendorPreference.Auto,
    val ports: CabinetPortOverrides = CabinetPortOverrides(),
    val doorCount: Int? = null,
    val probeTimeoutMs: Long = 450L,
    val heartbeat: HeartbeatConfig = HeartbeatConfig(),
    val calibrationStore: CalibrationStore = InMemoryCalibrationStore(),
    val logger: CabinetLogger = CabinetLogger.None,
) {
    init {
        require(doorCount == null || doorCount > 0) { "doorCount must be positive when provided." }
        require(probeTimeoutMs > 0) { "probeTimeoutMs must be greater than 0." }
    }
}

/**
 * 柜机驱动的厂商选择策略。
 */
sealed interface CabinetVendorPreference {

    /**
     * 使用 SDK 内置探测顺序自动选择驱动。
     */
    data object Auto : CabinetVendorPreference

    /**
     * 强制限定为某一个厂商实现。
     */
    data class Explicit(val vendor: CabinetVendor) : CabinetVendorPreference
}

/**
 * 柜机驱动的可选串口覆盖配置。
 *
 * 当现场机器的串口号与驱动默认值不同，或者调试阶段需要临时切换设备端口时，
 * 可以通过这里覆盖默认值。
 */
data class CabinetPortOverrides(
    val cabinet: SerialPortEndpoint? = null,
    val printer: SerialPortEndpoint? = null,
)

/**
 * 串口端点定义。
 */
data class SerialPortEndpoint(
    val device: String,
    val baudRate: Int,
) {
    init {
        require(device.isNotBlank()) { "device must not be blank." }
        require(baudRate > 0) { "baudRate must be greater than 0." }
    }
}

/**
 * [CabinetFacade] 使用的心跳与恢复策略。
 *
 * 用于约束多久检查一次驱动心跳、多久判定超时、启动后给多长预热时间、
 * 触发恢复后的冷却时间以及连续恢复失败阈值。
 */
data class HeartbeatConfig(
    val intervalMs: Long = 3_000L,
    val timeoutMs: Long = 9_000L,
    val startGraceMs: Long = 12_000L,
    val restartCooldownMs: Long = 10_000L,
    val maxRestartFailures: Int = 3,
) {
    init {
        require(intervalMs > 0) { "intervalMs must be greater than 0." }
        require(timeoutMs >= intervalMs) { "timeoutMs must be greater than or equal to intervalMs." }
        require(startGraceMs >= timeoutMs) {
            "startGraceMs must be greater than or equal to timeoutMs."
        }
        require(restartCooldownMs >= intervalMs) {
            "restartCooldownMs must be greater than or equal to intervalMs."
        }
        require(maxRestartFailures > 0) { "maxRestartFailures must be greater than 0." }
    }
}
