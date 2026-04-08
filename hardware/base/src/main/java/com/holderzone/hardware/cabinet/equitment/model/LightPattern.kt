package com.holderzone.hardware.cabinet.equitment.model

/** 灯光状态：常亮、熄灭、闪烁。 */
enum class LightState { On, Off, Blink }

/**
 * 灯光模式配置。
 *
 * @param state 灯光状态
 * @param blinkOnMs 闪烁时亮起时长（毫秒）
 * @param blinkOffMs 闪烁时熄灭时长（毫秒）
 * @param repeat 闪烁重复次数（0 表示无限或由设备决定）
 */
data class LightPattern(
    val state: LightState,
    val blinkOnMs: Long = 500,
    val blinkOffMs: Long = 500,
    val repeat: Int = 0,
)