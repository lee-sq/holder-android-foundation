package com.holderzone.hardware.cabinet.equitment.api

import com.holderzone.hardware.cabinet.equitment.model.HardwareResult
import kotlinx.coroutines.flow.Flow

/**
 * 称重能力接口。
 * 统一读数与流式订阅。
 */
interface Scale {

    /** 单次读数。 */
    fun read(): HardwareResult<Double?>

    /** 连续读数流，用于界面实时显示。 */
    fun stream(): Flow<Double>

    // 重量标定
    fun calibrationWeight(weight: Int): HardwareResult<Unit> = HardwareResult.Ok(Unit)

    // 清零
    fun zero(): HardwareResult<Unit> = HardwareResult.Ok(Unit)

    // 读取最真实的值
    fun readScaleWeight(): Flow<ScaleWeightDTO>? = null
}



data class ScaleWeightDTO(
    val rawWeight: Double,
    val weight: Double,
    val calibrationWeight: Double,
    val weightZero: Double?,
    val weightReal: Double?
)