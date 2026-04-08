package com.holderzone.hardware.cabinet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/**
 * 留样柜一体机 SDK 的高层统一入口。
 *
 * 一个 [CabinetFacade] 只代表一次宿主会话，不应该被做成全局单例重复复用。
 * 建议每个进程拥有者、每个页面，或者每个需要独立管理硬件生命周期的宿主，
 * 都创建一个新的 facade 实例。
 */
interface CabinetFacade : Closeable {

    /**
     * 柜机生命周期状态。
     *
     * 这个状态流描述的是“长生命周期状态”，例如未启动、启动中、运行中、恢复中、
     * 已停止、异常和已关闭，适合直接绑定到页面状态展示。
     */
    val state: StateFlow<CabinetState>

    /**
     * 柜机一次性事件流。
     *
     * 适合消费驱动选择结果、开门结果、重量变化、温度变化、打印完成、错误提示等
     * “事件型”数据，而不是长期持有型状态。
     */
    val events: Flow<CabinetEvent>

    /**
     * 当前激活驱动的能力矩阵。
     *
     * 当驱动完成探测和启动后，这里会反映当前厂商实现是否支持开门、称重、打印、
     * 温度读取、设置目标温度等能力。
     */
    val capabilities: StateFlow<CabinetCapabilities>

    /**
     * 柜门控制面。
     */
    val door: DoorController

    /**
     * 称重控制面。
     */
    val scale: ScaleController

    /**
     * 打印控制面。
     */
    val printer: PrinterController

    /**
     * 温度控制面。
     */
    val temperature: TemperatureController

    /**
     * 启动 facade，并根据配置选择具体厂商驱动。
     *
     * 当 [CabinetConfig.vendorPreference] 为自动探测时，
     * SDK 会按内置顺序尝试可用驱动；
     * 当为显式指定厂商时，只会尝试对应驱动。
     */
    suspend fun start(config: CabinetConfig): CabinetResult<Unit>

    /**
     * 停止当前驱动，但保留 facade 实例可再次启动。
     *
     * 适合页面离开但宿主对象仍想继续保留时调用。
     */
    suspend fun stop(): CabinetResult<Unit>

    /**
     * 彻底释放 facade 及其内部持有的全部资源。
     *
     * 调用后该实例进入不可复用状态，再次使用需要重新创建新的 facade。
     */
    override fun close()
}
