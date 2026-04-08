package com.holderzone.hardware.cabinet

import android.content.Context
import com.holderzone.hardware.cabinet.core.DefaultCabinetFacade
import com.holderzone.hardware.cabinet.driver.jw.sdk.JwSdkDriverFactory
import com.holderzone.hardware.cabinet.driver.jw.serial.JwSerialDriverFactory
import com.holderzone.hardware.cabinet.driver.star.StarDriverFactory

/**
 * Cabinet SDK facade 工厂。
 *
 * 对外只暴露一个创建入口，避免宿主直接感知内部 driver 组合细节。
 * 每次调用都会返回一个新的 [CabinetFacade] 实例，用于管理一次独立的硬件会话。
 */
object CabinetFacadeFactory {

    /**
     * 创建一个新的 [CabinetFacade]。
     *
     * 返回的新实例默认注册 Star、JW SDK、JW Serial 三个驱动工厂，
     * 后续的自动探测和显式厂商选择都由 facade 内部状态机统一处理。
     */
    fun create(context: Context): CabinetFacade {
        return DefaultCabinetFacade(
            context = context.applicationContext,
            driverFactories = listOf(
                StarDriverFactory(),
                JwSdkDriverFactory(),
                JwSerialDriverFactory(),
            ),
        )
    }
}
