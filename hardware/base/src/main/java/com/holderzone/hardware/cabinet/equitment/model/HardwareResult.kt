package com.holderzone.hardware.cabinet.equitment.model

/**
 * 统一硬件调用结果封装。
 *
 * - [Ok]: 调用成功并返回数据；
 * - [Err]: 调用失败，携带统一的 [HardwareError]。
 *
 * 业务层建议使用 `when(result)` 分支来处理不同情况，
 * 避免依赖异常来做控制流。
 */

sealed class HardwareResult<out T> {
    /** 成功结果，携带返回值。 */
    data class Ok<out T>(val value: T) : HardwareResult<T>()

    /** 失败结果，携带统一错误。 */
    data class Err(val error: HardwareError) : HardwareResult<Nothing>()
}