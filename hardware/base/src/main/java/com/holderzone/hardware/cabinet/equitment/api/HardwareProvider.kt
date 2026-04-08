package com.holderzone.hardware.cabinet.equitment.api

import android.content.Context
import com.holderzone.hardware.cabinet.equitment.model.HardwareConfig
import com.holderzone.hardware.cabinet.equitment.model.HardwareResult

/**
 * 厂商硬件 Provider 接口。
 *
 * 封装具体厂商的硬件能力实现与生命周期管理，
 * 通过可选能力字段（可能为 null）表达不同机型支持差异。
 */
interface HardwareProvider {
    val printer: Printer?
    val scale: Scale?
    val cabinetDoor: CabinetDoorController?
    val light: LightController?
    val temperature: TemperatureController?

    /**
     * 启动并初始化硬件资源。
     * @param context 上下文
     * @param config 统一配置入口
     */
    abstract fun start(context: Context, config: HardwareConfig): HardwareResult<Unit>

    /** 停止并释放资源。 */
    abstract fun stop(): HardwareResult<Unit>

    abstract fun hasInit(): Boolean

}

interface OpenDoorListener {
    fun openDoor(openResult: HardwareResult<Boolean>)
}