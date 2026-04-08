package com.holderzone.hardware.cabinet.equitment.model

/**
 * 柜门事件。
 *
 * @param doorNo 柜门编号
 * @param isOpen 是否打开
 * @param timestampMs 事件发生时间（毫秒）
 */

data class DoorEvent(
    val doorNo: Int,
    val isOpen: Boolean,
    val timestampMs: Long,
)