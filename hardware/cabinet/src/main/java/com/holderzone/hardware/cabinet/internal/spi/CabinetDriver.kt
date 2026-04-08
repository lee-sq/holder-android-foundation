package com.holderzone.hardware.cabinet.internal.spi

import com.holderzone.hardware.cabinet.CabinetCapabilities
import com.holderzone.hardware.cabinet.CabinetEvent
import com.holderzone.hardware.cabinet.CabinetResult
import com.holderzone.hardware.cabinet.CabinetVendor
import com.holderzone.hardware.cabinet.DoorOpenRequest
import com.holderzone.hardware.cabinet.DoorStateSnapshot
import com.holderzone.hardware.cabinet.PrintRequest
import com.holderzone.hardware.cabinet.PrintResultInfo
import com.holderzone.hardware.cabinet.TemperatureReading
import com.holderzone.hardware.cabinet.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cabinet SDK 内部使用的厂商驱动契约。
 *
 * 这一层只服务于 facade 状态机，不直接暴露给宿主。
 * 各厂商 driver 需要把自己的原始能力、事件和资源释放逻辑统一适配到这里。
 */
interface CabinetDriver : AutoCloseable {
    /**
     * 当前驱动对应的厂商。
     */
    val vendor: CabinetVendor

    /**
     * 当前驱动的能力矩阵。
     */
    val capabilities: CabinetCapabilities

    /**
     * 厂商事件流。
     */
    val events: Flow<CabinetEvent>

    /**
     * 最近一次心跳时间。
     *
     * facade 通过它判断驱动是否仍然活着，并决定是否触发恢复流程。
     */
    val lastHeartbeatAtMs: StateFlow<Long>

    /**
     * 柜门状态流。
     */
    val doorSnapshots: Flow<DoorStateSnapshot>

    /**
     * 重量状态流。
     */
    val weightReadings: Flow<WeightReading>

    /**
     * 温度状态流。
     */
    val temperatureReadings: Flow<TemperatureReading>

    /**
     * 当前驱动是否仍在运行。
     */
    fun isRunning(): Boolean

    /**
     * 停止驱动，但允许后续重新创建新的驱动实例。
     */
    suspend fun stop(): CabinetResult<Unit>

    /**
     * 打开一个或多个柜门。
     */
    suspend fun openDoors(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot>

    /**
     * 查询单个柜门状态。
     */
    suspend fun queryDoorState(door: Int): CabinetResult<DoorStateSnapshot>

    /**
     * 读取当前重量。
     */
    suspend fun readWeight(): CabinetResult<WeightReading>

    /**
     * 称重去皮或清零。
     */
    suspend fun zeroScale(): CabinetResult<Unit>

    /**
     * 按标准砝码执行标定。
     */
    suspend fun calibrateScale(standardWeightGrams: Double): CabinetResult<Unit>

    /**
     * 执行打印。
     */
    suspend fun print(request: PrintRequest): CabinetResult<PrintResultInfo>

    /**
     * 读取当前温度。
     */
    suspend fun readTemperature(): CabinetResult<TemperatureReading>

    /**
     * 设置目标温度。
     */
    suspend fun setTargetTemperature(celsius: Double): CabinetResult<Unit>

    /**
     * 释放驱动持有的所有底层资源。
     */
    override fun close()
}
