package com.holderzone.hardware.cabinet.equitment.api

import com.holderzone.hardware.cabinet.equitment.model.HardwareResult
import com.holderzone.hardware.cabinet.equitment.model.PrintJob


/**
 * 打印能力接口。
 * 为不同厂商的打印机提供统一的调用方式。
 */
interface Printer {
    /**
     * 执行打印任务。
     * @param job 打印任务配置
     * @return 成功返回 [HardwareResult.Ok]，失败返回 [HardwareResult.Err]
     */
    suspend fun print(job: PrintJob): HardwareResult<Unit> = HardwareResult.Ok(Unit)

    /**
     * 直接打印二进制数组
     * @param byteArray 二进制数组
     * @return 成功返回 [HardwareResult.Ok]，失败返回 [HardwareResult.Err]
     */
    suspend fun print(byteArray: ByteArray): HardwareResult<Unit> = HardwareResult.Ok(Unit)
}