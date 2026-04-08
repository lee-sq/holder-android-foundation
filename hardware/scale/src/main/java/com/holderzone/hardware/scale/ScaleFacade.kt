package com.holderzone.hardware.scale

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/**
 * 称重 SDK 的统一门面入口。
 *
 * 一个 [ScaleFacade] 只代表一次独立的设备会话，不应该做成全局单例反复复用。
 * 建议每个宿主页面、每个进程拥有者，或者每个需要独立管理称重生命周期的对象，
 * 都创建一个新的 facade 实例。
 */
interface ScaleFacade : Closeable {

    /**
     * 称重模块的长期生命周期状态。
     */
    val state: StateFlow<ScaleState>

    /**
     * 一次性事件流。
     *
     * 适合消费厂商选择结果、连接结果、重量更新提示和错误事件。
     */
    val events: Flow<ScaleEvent>

    /**
     * 当前已激活驱动的能力矩阵。
     */
    val capabilities: StateFlow<ScaleCapabilities>

    /**
     * 实时重量数据流。
     */
    val readings: Flow<WeightReading>

    /**
     * 启动称重 SDK，并根据配置解析具体厂商驱动。
     */
    suspend fun start(config: ScaleConfig): ScaleResult<Unit>

    /**
     * 读取一次最近的重量数据。
     *
     * 对于流式称重设备，这里返回的是驱动内部缓存的最近一次有效读数。
     */
    suspend fun readOnce(): ScaleResult<WeightReading>

    /**
     * 执行一次去皮。
     */
    suspend fun tare(): ScaleResult<Unit>

    /**
     * 执行一次清零。
     */
    suspend fun zero(): ScaleResult<Unit>

    /**
     * 停止当前驱动，但保留 facade 实例可再次启动。
     */
    suspend fun stop(): ScaleResult<Unit>

    /**
     * 彻底释放 facade 和内部驱动持有的全部资源。
     *
     * 调用后实例进入不可复用状态，如需再次使用，应重新创建新的 facade。
     */
    override fun close()
}
