package com.holderzone.hardware.cabinet.equitment.model

/**
 * 温度读数。
 *
 * @param celsius 摄氏度温度值
 * @param timestampMs 采样时间（毫秒）
 */

data class TemperatureReading(
    val celsius: Double,
    val timestampMs: Long,
)