package com.holderzone.hardware.cabinet.internal.spi

import android.content.Context
import com.holderzone.hardware.cabinet.CabinetConfig
import com.holderzone.hardware.cabinet.CabinetResult
import com.holderzone.hardware.cabinet.CabinetVendor

/**
 * Cabinet SDK 内部使用的驱动工厂。
 *
 * facade 通过多个 factory 做自动探测或显式厂商选择，
 * 真正的厂商驱动实例由各 factory 负责创建。
 */
interface CabinetDriverFactory {
    /**
     * 工厂对应的厂商类型。
     */
    val vendor: CabinetVendor

    /**
     * 打开一个驱动实例。
     *
     * 当 [probeOnly] 为 `true` 时，工厂只需要做足够的探测动作，
     * 用于判断该厂商在当前机器上是否可用；此时不应执行完整业务初始化。
     */
    suspend fun open(
        appContext: Context,
        config: CabinetConfig,
        probeOnly: Boolean,
    ): CabinetResult<CabinetDriver>
}
