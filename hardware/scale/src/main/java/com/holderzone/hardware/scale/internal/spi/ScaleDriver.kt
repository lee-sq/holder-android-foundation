package com.holderzone.hardware.scale.internal.spi

import com.holderzone.hardware.scale.ScaleCapabilities
import com.holderzone.hardware.scale.ScaleEvent
import com.holderzone.hardware.scale.ScaleResult
import com.holderzone.hardware.scale.ScaleVendor
import com.holderzone.hardware.scale.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 称重 SDK 内部使用的厂商驱动契约。
 */
interface ScaleDriver : AutoCloseable {
    val vendor: ScaleVendor

    val capabilities: ScaleCapabilities

    val events: Flow<ScaleEvent>

    val readings: Flow<WeightReading>

    /**
     * 最近一次收到有效数据的时间戳。
     *
     * 自动探测阶段会依赖这个时间戳判断当前驱动是否真的接收到了设备数据。
     */
    val lastSignalAtMs: StateFlow<Long>

    fun isRunning(): Boolean

    suspend fun stop(): ScaleResult<Unit>

    suspend fun readOnce(): ScaleResult<WeightReading>

    suspend fun tare(): ScaleResult<Unit>

    suspend fun zero(): ScaleResult<Unit>

    override fun close()
}
