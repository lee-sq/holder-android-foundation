package com.holderzone.hardware.cabinet.equitment.model

/**
 * 称重读数。
 *
 * @param grams 克为单位的重量值
 * @param timestampMs 读数时间戳（毫秒）
 */

data class WeightReading(
    val grams: Int,
    val timestampMs: Long,
)