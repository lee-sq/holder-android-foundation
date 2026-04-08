package com.holderzone.hardware.cabinet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前 SDK 支持的留样柜厂商实现。
 */
enum class CabinetVendor {
    STAR,
    JW_SDK,
    JW_SERIAL,
}

/**
 * 当前已选驱动的能力矩阵。
 *
 * facade 启动完成后，宿主可以通过该模型判断当前设备是否支持开门、读门状态、
 * 读重、流式称重、去皮、标定、标签打印、原始字节打印、温度读取和目标温度设置。
 */
data class CabinetCapabilities(
    val selectedVendor: CabinetVendor? = null,
    val openDoor: Boolean = false,
    val queryDoorState: Boolean = false,
    val readWeight: Boolean = false,
    val streamWeight: Boolean = false,
    val zeroScale: Boolean = false,
    val calibrateScale: Boolean = false,
    val printLabel: Boolean = false,
    val printRawBytes: Boolean = false,
    val readTemperature: Boolean = false,
    val setTargetTemperature: Boolean = false,
) {
    companion object {
        val NONE = CabinetCapabilities()
    }
}

/**
 * 柜机长期生命周期状态。
 */
sealed interface CabinetState {
    /**
     * 未启动。
     */
    data object Idle : CabinetState

    /**
     * 启动中。
     */
    data class Starting(
        val config: CabinetConfig,
    ) : CabinetState

    /**
     * 正在运行，且已经绑定具体厂商驱动。
     */
    data class Running(
        val vendor: CabinetVendor,
        val capabilities: CabinetCapabilities,
    ) : CabinetState

    /**
     * 正在做故障恢复。
     */
    data class Recovering(
        val vendor: CabinetVendor,
        val attempt: Int,
    ) : CabinetState

    /**
     * 已停止，但 facade 仍可复用。
     */
    data class Stopped(
        val lastVendor: CabinetVendor? = null,
    ) : CabinetState

    /**
     * 已进入错误状态。
     */
    data class Error(
        val vendor: CabinetVendor?,
        val error: CabinetError,
    ) : CabinetState

    /**
     * 已彻底关闭，不可再次使用。
     */
    data object Closed : CabinetState
}

/**
 * 柜机一次性事件流。
 */
sealed interface CabinetEvent {
    /**
     * 已经选中某个厂商驱动。
     */
    data class VendorSelected(val vendor: CabinetVendor) : CabinetEvent

    /**
     * 柜门状态快照发生变化。
     */
    data class DoorSnapshotChanged(val snapshot: DoorStateSnapshot) : CabinetEvent

    /**
     * 某次开门请求已成功打开全部目标柜门。
     */
    data class DoorOpened(val snapshot: DoorStateSnapshot) : CabinetEvent

    /**
     * 新的重量数据到达。
     */
    data class WeightUpdated(val reading: WeightReading) : CabinetEvent

    /**
     * 新的温度数据到达。
     */
    data class TemperatureUpdated(val reading: TemperatureReading) : CabinetEvent

    /**
     * 打印请求执行完成。
     */
    data class PrintCompleted(val result: PrintResultInfo) : CabinetEvent

    /**
     * 通用信息事件。
     */
    data class Info(val message: String) : CabinetEvent

    /**
     * 通用错误事件。
     */
    data class Error(val error: CabinetError) : CabinetEvent
}

/**
 * Cabinet SDK 使用的函数式结果包装。
 */
sealed interface CabinetResult<out T> {
    data class Ok<T>(val value: T) : CabinetResult<T>

    data class Err(val error: CabinetError) : CabinetResult<Nothing>
}

/**
 * Cabinet SDK 统一错误模型。
 *
 * 所有驱动、状态机、打印、串口和厂商 SDK 异常都应尽量映射到这一层，
 * 便于宿主统一展示、重试和统计。
 */
sealed class CabinetError(
    open val code: String,
    override val message: String,
    open val vendor: CabinetVendor? = null,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class Configuration(
        message: String,
        cause: Throwable? = null,
    ) : CabinetError(
        code = "CONFIGURATION",
        message = message,
        cause = cause,
    )

    class DeviceUnavailable(
        message: String,
        vendor: CabinetVendor? = null,
        cause: Throwable? = null,
    ) : CabinetError(
        code = "DEVICE_UNAVAILABLE",
        message = message,
        vendor = vendor,
        cause = cause,
    )

    class Unsupported(
        message: String,
        vendor: CabinetVendor? = null,
    ) : CabinetError(
        code = "UNSUPPORTED",
        message = message,
        vendor = vendor,
    )

    class Communication(
        message: String,
        vendor: CabinetVendor? = null,
        cause: Throwable? = null,
    ) : CabinetError(
        code = "COMMUNICATION",
        message = message,
        vendor = vendor,
        cause = cause,
    )

    class OperationFailed(
        message: String,
        vendor: CabinetVendor? = null,
        cause: Throwable? = null,
    ) : CabinetError(
        code = "OPERATION_FAILED",
        message = message,
        vendor = vendor,
        cause = cause,
    )

    class Closed : CabinetError(
        code = "CLOSED",
        message = "CabinetFacade is already closed.",
    )
}

/**
 * 开门请求。
 *
 * 对外统一使用 1-based 门号语义，也就是第一格柜门传 `1`，而不是 `0`。
 */
data class DoorOpenRequest(
    val doors: List<Int>,
    val timeoutMs: Long = 5_500L,
    val retryIntervalMs: Long = 1_500L,
) {
    init {
        require(doors.isNotEmpty()) { "doors must not be empty." }
        require(doors.all { it > 0 }) { "doors must be 1-based positive numbers." }
        require(timeoutMs > 0) { "timeoutMs must be greater than 0." }
        require(retryIntervalMs > 0) { "retryIntervalMs must be greater than 0." }
    }
}

/**
 * 当前柜门状态快照。
 *
 * [requestedDoors] 表示本次关心的目标门，
 * [openedDoors] 表示这些门里已经处于打开状态的门。
 */
data class DoorStateSnapshot(
    val requestedDoors: Set<Int>,
    val openedDoors: Set<Int>,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    val allRequestedOpened: Boolean
        get() = requestedDoors.all(openedDoors::contains)
}

/**
 * 来自柜机称重模块的重量读数。
 */
data class WeightReading(
    val grams: Double,
    val timestampMillis: Long = System.currentTimeMillis(),
    val rawGrams: Double? = null,
    val zeroOffsetGrams: Double? = null,
    val slope: Double? = null,
)

/**
 * Cabinet SDK 支持的打印请求。
 */
sealed interface PrintRequest {
    /**
     * 标签打印请求。
     */
    data class Label(
        val title: String? = null,
        val lines: List<String> = emptyList(),
        val qrCode: String? = null,
        val widthMm: Int = 50,
        val heightMm: Int = 40,
    ) : PrintRequest {
        init {
            require(widthMm > 0) { "widthMm must be greater than 0." }
            require(heightMm > 0) { "heightMm must be greater than 0." }
        }
    }

    /**
     * 原始字节打印请求。
     */
    data class RawBytes(
        val bytes: ByteArray,
    ) : PrintRequest {
        init {
            require(bytes.isNotEmpty()) { "bytes must not be empty." }
        }
    }
}

/**
 * 打印结果元数据。
 */
data class PrintResultInfo(
    val vendor: CabinetVendor,
    val requestType: RequestType,
    val timestampMillis: Long = System.currentTimeMillis(),
    val byteCount: Int,
) {
    enum class RequestType {
        LABEL,
        RAW_BYTES,
    }
}

/**
 * 柜机温度读数。
 */
data class TemperatureReading(
    val celsius: Double,
    val timestampMillis: Long = System.currentTimeMillis(),
    val targetCelsius: Double? = null,
)

/**
 * 柜门控制契约。
 */
interface DoorController {
    /**
     * 柜门状态流。
     */
    val states: Flow<DoorStateSnapshot>

    /**
     * 发起一次开门请求。
     */
    suspend fun open(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot>

    /**
     * 查询单个门的当前状态。
     */
    suspend fun query(door: Int): CabinetResult<DoorStateSnapshot>
}

/**
 * 称重控制契约。
 */
interface ScaleController {
    /**
     * 重量读数流。
     */
    val readings: Flow<WeightReading>

    /**
     * 主动读取一次当前重量。
     */
    suspend fun read(): CabinetResult<WeightReading>

    /**
     * 去皮或清零。
     */
    suspend fun zero(): CabinetResult<Unit>

    /**
     * 根据标准砝码做一次标定。
     */
    suspend fun calibrate(standardWeightGrams: Double): CabinetResult<Unit>
}

/**
 * 打印控制契约。
 */
interface PrinterController {
    /**
     * 执行一次打印。
     */
    suspend fun print(request: PrintRequest): CabinetResult<PrintResultInfo>
}

/**
 * 温度控制契约。
 */
interface TemperatureController {
    /**
     * 温度读数流。
     */
    val readings: Flow<TemperatureReading>

    /**
     * 主动读取一次当前温度。
     */
    suspend fun read(): CabinetResult<TemperatureReading>

    /**
     * 设置目标温度。
     *
     * 如果当前厂商不支持，会返回 [CabinetError.Unsupported]。
     */
    suspend fun setTarget(celsius: Double): CabinetResult<Unit>
}

/**
 * 柜机驱动使用的标定参数存储接口。
 *
 * SDK 本身不强依赖 MMKV、DataStore 或数据库，宿主可自由实现自己的持久化方案。
 */
interface CalibrationStore {
    /**
     * 读取某个厂商的称重斜率。
     */
    suspend fun readWeightSlope(vendor: CabinetVendor): Double?

    /**
     * 写入某个厂商的称重斜率。
     */
    suspend fun writeWeightSlope(vendor: CabinetVendor, slope: Double)

    /**
     * 清除某个厂商的称重斜率。
     */
    suspend fun clear(vendor: CabinetVendor)
}

/**
 * 默认内存版 [CalibrationStore]。
 *
 * 适合 sample、测试或不需要持久化标定参数的场景。
 */
class InMemoryCalibrationStore : CalibrationStore {
    private val slopes = MutableStateFlow<Map<CabinetVendor, Double>>(emptyMap())

    /**
     * 当前内存快照，便于测试或调试查看。
     */
    val snapshot: StateFlow<Map<CabinetVendor, Double>> = slopes.asStateFlow()

    override suspend fun readWeightSlope(vendor: CabinetVendor): Double? {
        return slopes.value[vendor]
    }

    override suspend fun writeWeightSlope(vendor: CabinetVendor, slope: Double) {
        slopes.value = slopes.value.toMutableMap().apply {
            put(vendor, slope)
        }
    }

    override suspend fun clear(vendor: CabinetVendor) {
        slopes.value = slopes.value.toMutableMap().apply {
            remove(vendor)
        }
    }
}

/**
 * Cabinet SDK 唯一日志出口。
 *
 * 宿主可以桥接到自己的日志系统，SDK 内部不直接依赖业务日志实现。
 */
interface CabinetLogger {
    /**
     * 调试日志。
     */
    fun debug(tag: String, message: String)

    /**
     * 信息日志。
     */
    fun info(tag: String, message: String)

    /**
     * 警告日志。
     */
    fun warn(tag: String, message: String)

    /**
     * 错误日志。
     */
    fun error(tag: String, message: String, throwable: Throwable? = null)

    /**
     * 空实现，适合不需要日志输出的场景。
     */
    data object None : CabinetLogger {
        override fun debug(tag: String, message: String) = Unit

        override fun info(tag: String, message: String) = Unit

        override fun warn(tag: String, message: String) = Unit

        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
