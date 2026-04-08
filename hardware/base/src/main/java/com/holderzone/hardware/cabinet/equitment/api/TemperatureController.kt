package com.holderzone.hardware.cabinet.equitment.api

import com.holderzone.hardware.cabinet.equitment.model.HardwareResult
import com.holderzone.hardware.cabinet.equitment.model.TemperatureReading
import kotlinx.coroutines.flow.Flow

/**
 * 温控能力接口。
 */
interface TemperatureController {
    /** 设置目标温度（摄氏度）。 */
    fun target(celsius: Double): HardwareResult<Unit>

    /** 单次温度读取。 */
    fun read(): HardwareResult<TemperatureReading>

    /** 温度读数流，用于连续监控。 */
    fun stream(): Flow<TemperatureReading>
}