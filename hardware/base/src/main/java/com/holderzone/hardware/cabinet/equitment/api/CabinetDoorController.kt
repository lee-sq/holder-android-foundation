package com.holderzone.hardware.cabinet.equitment.api

import com.holderzone.hardware.cabinet.equitment.model.HardwareResult


/**
 * 柜门控制能力接口。
 * 统一开/关/查询与事件流订阅。
 */
interface CabinetDoorController {
    /**
     * 打开指定柜门。
     * @param doors 柜门编号列表
     * @param listener 开启后的回调
     */
    fun open(doors: List<Int>, listener: OpenDoorListener)


    /**
     * 查询单个柜门是否打开。
     * @param door 柜门编号
     * @return `true` 表示打开，`false` 表示关闭
     */
    fun query(door: Int): HardwareResult<Boolean>

}