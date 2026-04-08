package com.holderzone.hardware.cabinet.equitment.model

/**
 * 统一的硬件错误模型。
 *
 * @param code 机器可读错误码，便于跨厂商映射与统计
 * @param message 人类可读错误信息，适合日志与弹窗展示
 * @param cause 原始异常，可选，用于调试与溯源
 */

open class HardwareError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
)

/** 请求超时，例如串口/网络读写超过阈值。 */
class Timeout(message: String = "Timeout", cause: Throwable? = null) :
    HardwareError("TIMEOUT", message, cause)

/** 设备忙或正被占用。 */
class Busy(message: String = "Device busy", cause: Throwable? = null) :
    HardwareError("BUSY", message, cause)

/** 找不到目标设备/资源/端口。 */
class NotFound(message: String = "Not found", cause: Throwable? = null) :
    HardwareError("NOT_FOUND", message, cause)

/** 不支持的能力或操作。 */
class Unsupported(message: String = "Unsupported", cause: Throwable? = null) :
    HardwareError("UNSUPPORTED", message, cause)

/** 权限不足或被拒绝。 */
class PermissionDenied(message: String = "Permission denied", cause: Throwable? = null) :
    HardwareError("PERMISSION_DENIED", message, cause)

/** 通用 I/O 错误，例如读写失败。 */
class Io(message: String = "I/O error", cause: Throwable? = null) :
    HardwareError("IO", message, cause)

/** 协议层错误，例如帧格式/校验失败。 */
class Protocol(message: String = "Protocol error", cause: Throwable? = null) :
    HardwareError("PROTOCOL", message, cause)

/** 未归类的未知错误。 */
class Unknown(message: String = "Unknown error", cause: Throwable? = null) :
    HardwareError("UNKNOWN", message, cause)