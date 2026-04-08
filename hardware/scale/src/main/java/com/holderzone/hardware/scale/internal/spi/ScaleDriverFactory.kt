package com.holderzone.hardware.scale.internal.spi

import android.content.Context
import com.holderzone.hardware.scale.ScaleConfig
import com.holderzone.hardware.scale.ScalePortConfig
import com.holderzone.hardware.scale.ScaleResult
import com.holderzone.hardware.scale.ScaleVendor

/**
 * 称重驱动工厂。
 *
 * facade 负责先选厂商，再为该厂商解析需要尝试的串口候选列表；
 * 每次真正打开 driver 时，都会把当前正在尝试的 [port] 明确传入。
 */
interface ScaleDriverFactory {
    val vendor: ScaleVendor
    val defaultPortCandidates: List<ScalePortConfig>

    /**
     * 打开一个 driver 实例。
     *
     * 当 [probeOnly] 为 `true` 时，工厂只需要完成探测所需的最小初始化。
     */
    suspend fun open(
        appContext: Context,
        config: ScaleConfig,
        port: ScalePortConfig,
        probeOnly: Boolean,
    ): ScaleResult<ScaleDriver>
}
