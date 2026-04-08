package com.holderzone.hardware.cabinet

internal const val GRAMS_PER_KILOGRAM = 1_000.0

/**
 * 厂商协议原始回传单位是 kg，Cabinet SDK 对外统一暴露为 g。
 */
internal fun kilogramsToGrams(kilograms: Double): Double {
    return kilograms * GRAMS_PER_KILOGRAM
}
