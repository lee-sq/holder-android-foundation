package com.holderzone.hardware.cabinet.equitment.model

/**
 * 统一硬件配置入口。
 *
 * @param vendorId 厂商/机型标识，用于选择具体 Provider
 * @param serialPorts 串口配置映射（例如 "door" -> 串口）
 * @param extras 其他扩展参数（键值对）
 */

data class HardwareConfig(
    val vendorId: Int = -1,
    val serialPorts: Map<String, SerialPortConfig> = emptyMap(),
    val extras: Map<String, Any?> = emptyMap(),
)