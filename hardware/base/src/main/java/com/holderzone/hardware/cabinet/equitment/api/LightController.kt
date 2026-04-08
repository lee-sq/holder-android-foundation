package com.holderzone.hardware.cabinet.equitment.api

import com.holderzone.hardware.cabinet.equitment.model.HardwareResult
import com.holderzone.hardware.cabinet.equitment.model.LightPattern
/**
 * 灯光控制能力接口。
 */
interface LightController {
    /**
     * 应用灯光模式。
     * @param pattern 灯光模式配置
     */
    fun apply(pattern: LightPattern): HardwareResult<Unit>
}