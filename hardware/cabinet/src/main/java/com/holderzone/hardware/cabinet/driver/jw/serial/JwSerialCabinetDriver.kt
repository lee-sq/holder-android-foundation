package com.holderzone.hardware.cabinet.driver.jw.serial

import android.content.Context
import com.bjw.bean.ComBean
import com.bjw.utils.SerialHelper
import com.holderzone.hardware.cabinet.CabinetCapabilities
import com.holderzone.hardware.cabinet.CabinetConfig
import com.holderzone.hardware.cabinet.CabinetError
import com.holderzone.hardware.cabinet.CabinetEvent
import com.holderzone.hardware.cabinet.CabinetResult
import com.holderzone.hardware.cabinet.CabinetVendor
import com.holderzone.hardware.cabinet.DoorOpenRequest
import com.holderzone.hardware.cabinet.DoorStateSnapshot
import com.holderzone.hardware.cabinet.PrintRequest
import com.holderzone.hardware.cabinet.PrintResultInfo
import com.holderzone.hardware.cabinet.SerialPortEndpoint
import com.holderzone.hardware.cabinet.TemperatureReading
import com.holderzone.hardware.cabinet.WeightReading
import com.holderzone.hardware.cabinet.kilogramsToGrams
import com.holderzone.hardware.cabinet.internal.spi.CabinetDriver
import com.holderzone.hardware.cabinet.internal.spi.CabinetDriverFactory
import com.holderzone.hardware.cabinet.print.CabinetLabelRenderer
import com.holderzone.hardware.cabinet.print.CommonTSPLPrintProvide
import com.holderzone.hardware.cabinet.print.PrintResult
import com.tongdeliu.jwbaselib.utils.CRCCheck
import com.tongdeliu.jwbaselib.utils.CRCCheckUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

class JwSerialDriverFactory : CabinetDriverFactory {
    override val vendor: CabinetVendor = CabinetVendor.JW_SERIAL

    override suspend fun open(
        appContext: Context,
        config: CabinetConfig,
        probeOnly: Boolean,
    ): CabinetResult<CabinetDriver> {
        return JwSerialCabinetDriver.create(appContext, config, probeOnly)
    }
}

/**
 * 基于 JW 串口协议的柜机驱动实现。
 *
 * 当官方 SDK 不可用或现场更适合直接走串口协议时，由该驱动负责协议拼包、
 * 帧解析、心跳更新和能力适配。
 */
internal class JwSerialCabinetDriver private constructor(
    private val appContext: Context,
    private val config: CabinetConfig,
    private val probeOnly: Boolean,
) : CabinetDriver {

    override val vendor: CabinetVendor = CabinetVendor.JW_SERIAL

    override val capabilities: CabinetCapabilities = CabinetCapabilities(
        selectedVendor = vendor,
        openDoor = true,
        queryDoorState = true,
        readWeight = true,
        streamWeight = true,
        zeroScale = true,
        calibrateScale = true,
        printLabel = !probeOnly,
        printRawBytes = !probeOnly,
        readTemperature = true,
        setTargetTemperature = true,
    )

    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableEvents = MutableSharedFlow<CabinetEvent>(extraBufferCapacity = 64)
    private val mutableHeartbeat = MutableStateFlow(0L)
    private val mutableDoorSnapshots = MutableSharedFlow<DoorStateSnapshot>(replay = 1, extraBufferCapacity = 16)
    private val mutableWeightReadings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 16)
    private val mutableTemperatureReadings = MutableSharedFlow<TemperatureReading>(replay = 1, extraBufferCapacity = 16)

    override val events: Flow<CabinetEvent> = mutableEvents
    override val lastHeartbeatAtMs = mutableHeartbeat.asStateFlow()
    override val doorSnapshots: Flow<DoorStateSnapshot> = mutableDoorSnapshots
    override val weightReadings: Flow<WeightReading> = mutableWeightReadings
    override val temperatureReadings: Flow<TemperatureReading> = mutableTemperatureReadings

    private val cabinetDoorCount = config.doorCount ?: DEFAULT_DOOR_COUNT
    private val serialHelper = JwCabinetSerialHelper(::readPortData)
    private val printer = CommonTSPLPrintProvide()
    private val doorStateMap = mutableMapOf<Int, Boolean>()
    // 写通道与轮询任务都只属于当前 driver，会在 stop/close 时整体释放。
    private var commandQueue: Channel<String>? = null
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var readChannelCommand: String? = null
    private var weightSlope = 1.0
    private var lastRawWeight = 1.0
    private var lastWeightReading: WeightReading? = null
    private var lastTemperatureReading: TemperatureReading? = null

    companion object {
        private const val WEIGHT_STATE = 2
        private const val DOOR_LIGHT_STATE_1 = 16
        private const val DOOR_LIGHT_STATE_2 = 17
        private const val DOOR_LIGHT_STATE_3 = 18
        private const val LOCK_THRESHOLD = 17_000
        private const val DEFAULT_DOOR_COUNT = 8
        private const val LIGHT_ON_COMMAND = "02100005000102AAAA4C2A"
        private const val LIGHT_OFF_COMMAND = "021000050001020000B2F5"
        private const val OUT_DOOR_INDEX = 0

        private val DEFAULT_CABINET_PORT = SerialPortEndpoint("/dev/ttyS2", 9600)
        private val DEFAULT_PRINTER_PORT = SerialPortEndpoint("/dev/ttyS3", 9600)

        private val READ_WEIGHT_COMMAND by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val chars = charArrayOf(
                0x02.toChar(),
                0x03.toChar(),
                0x00.toChar(),
                0x00.toChar(),
                0x00.toChar(),
                0x12.toChar(),
            )
            val crc = CRCCheck.CRC16(chars)
            FuncUtils.CharArrToHex(chars, crc)
        }

        suspend fun create(
            appContext: Context,
            config: CabinetConfig,
            probeOnly: Boolean,
        ): CabinetResult<CabinetDriver> {
            val driver = JwSerialCabinetDriver(appContext, config, probeOnly)
            return driver.start()
        }
    }

    override fun isRunning(): Boolean = running.get()

    override suspend fun stop(): CabinetResult<Unit> {
        return shutdown()
    }

    override fun close() {
        runBlocking {
            shutdown()
        }
    }

    private fun shutdown(): CabinetResult<Unit> {
        return runCatching {
            readJob?.cancel()
            writeJob?.cancel()
            commandQueue?.cancel()
            commandQueue?.close()
            readJob = null
            writeJob = null
            commandQueue = null
            // 串口、打印串口和协程任务都必须在关闭时彻底回收，避免下次启动占用老资源。
            serialHelper.closeSerial()
            printer.disconnect()
            scope.coroutineContext.cancelChildren()
            mutableHeartbeat.value = 0L
            running.set(false)
            CabinetResult.Ok(Unit)
        }.getOrElse { throwable ->
            CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = throwable.message ?: "Failed to stop JW serial driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun openDoors(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot> {
        if (!running.get()) {
            return CabinetResult.Err(CabinetError.DeviceUnavailable("JW serial driver is not running.", vendor))
        }
        val targets = request.doors.toSet()
        if (targets.any { it > cabinetDoorCount }) {
            return CabinetResult.Err(
                CabinetError.Configuration("Door index exceeds configured doorCount=$cabinetDoorCount.")
            )
        }
        targets.forEach { sendCommand(buildOpenDoorCommand(it - 1)) }
        val deadline = System.currentTimeMillis() + request.timeoutMs
        var lastRetry = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val snapshot = snapshotFor(targets)
            if (snapshot.allRequestedOpened) {
                mutableDoorSnapshots.emit(snapshot)
                mutableEvents.emit(CabinetEvent.DoorOpened(snapshot))
                return CabinetResult.Ok(snapshot)
            }
            val now = System.currentTimeMillis()
            if (now - lastRetry >= request.retryIntervalMs) {
                // 未打开的门继续重发开门指令，增强弱串口环境下的成功率。
                targets.filterNot { doorStateMap[it] == true }.forEach { sendCommand(buildOpenDoorCommand(it - 1)) }
                lastRetry = now
            }
            delay(200L)
        }
        return CabinetResult.Err(
            CabinetError.OperationFailed(
                message = "Timed out while opening JW serial doors $targets.",
                vendor = vendor,
            )
        )
    }

    override suspend fun queryDoorState(door: Int): CabinetResult<DoorStateSnapshot> {
        if (door <= 0 || door > cabinetDoorCount) {
            return CabinetResult.Err(
                CabinetError.Configuration("door must be within 1..$cabinetDoorCount for vendor $vendor.")
            )
        }
        return CabinetResult.Ok(snapshotFor(setOf(door)))
    }

    override suspend fun readWeight(): CabinetResult<WeightReading> {
        val reading = lastWeightReading
        return if (reading != null) {
            CabinetResult.Ok(reading)
        } else {
            CabinetResult.Err(CabinetError.DeviceUnavailable("No weight reading is available yet.", vendor))
        }
    }

    override suspend fun zeroScale(): CabinetResult<Unit> {
        sendCommand("02100002000102AAAA4D9D")
        return CabinetResult.Ok(Unit)
    }

    override suspend fun calibrateScale(standardWeightGrams: Double): CabinetResult<Unit> {
        if (standardWeightGrams <= 0.0) {
            return CabinetResult.Err(CabinetError.Configuration("standardWeightGrams must be greater than 0."))
        }
        if (lastRawWeight <= 0.0) {
            return CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = "A positive raw weight reading is required before calibration.",
                    vendor = vendor,
                )
            )
        }
        val slope = standardWeightGrams / lastRawWeight
        if (!slope.isFinite() || slope <= 0.0) {
            return CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = "Calculated calibration slope is invalid.",
                    vendor = vendor,
                )
            )
        }
        weightSlope = slope
        // 直接串口方案不依赖业务存储实现，仍统一通过 CalibrationStore 回写标定斜率。
        config.calibrationStore.writeWeightSlope(vendor, slope)
        return CabinetResult.Ok(Unit)
    }

    override suspend fun print(request: PrintRequest): CabinetResult<PrintResultInfo> {
        if (probeOnly) {
            return CabinetResult.Err(CabinetError.Unsupported("Probe mode does not allow printing.", vendor))
        }
        val payload = when (request) {
            is PrintRequest.Label -> CabinetLabelRenderer.renderCommonLabel(request)
            is PrintRequest.RawBytes -> request.bytes
        }
        return when (printer.print(payload)) {
            is PrintResult.Success -> CabinetResult.Ok(
                PrintResultInfo(
                    vendor = vendor,
                    requestType = request.toRequestType(),
                    byteCount = payload.size,
                )
            )

            is PrintResult.Failed -> CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = "JW serial print failed.",
                    vendor = vendor,
                )
            )
        }
    }

    override suspend fun readTemperature(): CabinetResult<TemperatureReading> {
        val reading = lastTemperatureReading
        return if (reading != null) {
            CabinetResult.Ok(reading)
        } else {
            CabinetResult.Err(CabinetError.DeviceUnavailable("No temperature reading is available yet.", vendor))
        }
    }

    override suspend fun setTargetTemperature(celsius: Double): CabinetResult<Unit> {
        setRealtimeTemperature(celsius.toInt())
        return CabinetResult.Ok(Unit)
    }

    private suspend fun start(): CabinetResult<CabinetDriver> {
        return runCatching {
            weightSlope = config.calibrationStore.readWeightSlope(vendor) ?: 1.0
            readChannelCommand = buildReadChannelCommand()
            val cabinetPort = config.ports.cabinet ?: DEFAULT_CABINET_PORT
            val printerPort = config.ports.printer ?: DEFAULT_PRINTER_PORT
            serialHelper.open(cabinetPort.device, cabinetPort.baudRate)
            commandQueue = Channel(capacity = 100, onBufferOverflow = BufferOverflow.SUSPEND)
            writeJob = scope.launch {
                val queue = commandQueue ?: return@launch
                for (command in queue) {
                    try {
                        // 所有下发指令统一从一个写协程串行发送，避免多协程直接抢串口。
                        serialHelper.sendHex(command)
                        delay(120L)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                    }
                }
            }
            readJob = scope.launch {
                val queue = commandQueue ?: return@launch
                val readDoorCommand = readChannelCommand ?: return@launch
                while (isActive) {
                    try {
                        // 串口方案没有厂商 SDK 的主动推送，需要靠轮询重量和门状态驱动心跳。
                        queue.send(READ_WEIGHT_COMMAND)
                        delay(240L)
                        queue.send(readDoorCommand)
                        delay(240L)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                    }
                }
            }
            if (!probeOnly) {
                printer.connect(printerPort.device, printerPort.baudRate)
            }
            running.set(true)
            CabinetResult.Ok(this as CabinetDriver)
        }.getOrElse { throwable ->
            close()
            CabinetResult.Err(
                CabinetError.DeviceUnavailable(
                    message = throwable.message ?: "Failed to initialize JW serial driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private fun sendCommand(command: String) {
        commandQueue?.trySend(command)
    }

    private fun readPortData(hexData: String) {
        if (hexData.length < 4) {
            return
        }
        markHeartbeat()
        val address = FuncUtils.HexToByte(hexData.take(2)).toInt() and 0xFF
        when (address) {
            WEIGHT_STATE -> parseWeightFrame(hexData)
            DOOR_LIGHT_STATE_1,
            DOOR_LIGHT_STATE_2,
            DOOR_LIGHT_STATE_3,
            -> parseDoorAndTemperatureFrame(hexData)

            else -> Unit
        }
    }

    private fun parseWeightFrame(hexData: String) {
        if (hexData.length < 32) {
            return
        }
        // 该协议里零点和实时重量分散在不同字段，需要先解出零点再计算净重。
        val zeroWeightGrams = kilogramsToGrams(FuncUtils.HexToInt(hexData.substring(18, 22)).toDouble())
        val realWeightGrams = kilogramsToGrams(FuncUtils.HexToInt(hexData.substring(26, 30)).toDouble())
        lastRawWeight = realWeightGrams - zeroWeightGrams
        val reading = WeightReading(
            grams = lastRawWeight * weightSlope,
            rawGrams = lastRawWeight,
            zeroOffsetGrams = zeroWeightGrams,
            slope = weightSlope,
        )
        lastWeightReading = reading
        mutableWeightReadings.tryEmit(reading)
        mutableEvents.tryEmit(CabinetEvent.WeightUpdated(reading))

        val isOuterLightOpen = FuncUtils.HexToInt(hexData.substring(22, 26)) != 0
        val outsideDoorOpen = FuncUtils.HexToInt(hexData.substring(30, 34)) != 0
        // 保持与旧设备逻辑一致：外门关闭后自动关灯，打开时自动开灯。
        if (!outsideDoorOpen && isOuterLightOpen) {
            sendCommand(LIGHT_OFF_COMMAND)
        } else if (outsideDoorOpen && !isOuterLightOpen) {
            sendCommand(LIGHT_ON_COMMAND)
        }
    }

    private fun parseDoorAndTemperatureFrame(hexData: String) {
        if (hexData.length < 10) {
            return
        }
        val temperature = FuncUtils.HexToInt(hexData.substring(6, 10)) - 40
        for (index in 0..cabinetDoorCount) {
            val startIndex = (5 + index * 2) * 2
            val endIndex = (7 + index * 2) * 2
            if (endIndex > hexData.length) {
                break
            }
            val rawDoorValue = FuncUtils.HexToInt(hexData.substring(startIndex, endIndex))
            if (index > 0) {
                // 对外门号统一为 1-based，因此 index=1 对应第一格柜门。
                doorStateMap[index] = rawDoorValue > LOCK_THRESHOLD
            }
        }
        setRealtimeTemperature(temperature + 40)
        val temperatureReading = TemperatureReading(celsius = temperature.toDouble())
        lastTemperatureReading = temperatureReading
        val snapshot = snapshotFor(doorStateMap.keys)
        mutableDoorSnapshots.tryEmit(snapshot)
        mutableTemperatureReadings.tryEmit(temperatureReading)
        mutableEvents.tryEmit(CabinetEvent.DoorSnapshotChanged(snapshot))
        mutableEvents.tryEmit(CabinetEvent.TemperatureUpdated(temperatureReading))
    }

    private fun snapshotFor(requestedDoors: Set<Int>): DoorStateSnapshot {
        val openedDoors = requestedDoors.filterTo(linkedSetOf()) { doorStateMap[it] == true }
        return DoorStateSnapshot(
            requestedDoors = requestedDoors,
            openedDoors = openedDoors,
        )
    }

    private fun buildReadChannelCommand(): String {
        // 柜门状态读取长度需要把“外门 + 内门”一起考虑，因此是 doorCount + 1。
        val chars = charArrayOf(
            16.toChar(),
            0x03.toChar(),
            0x00.toChar(),
            0x00.toChar(),
            0x00.toChar(),
            (cabinetDoorCount + 1).toChar(),
        )
        val crc = CRCCheck.CRC16(chars)
        return FuncUtils.CharArrToHex(chars, crc)
    }

    private fun setRealtimeTemperature(temperature: Int) {
        // 协议要求设置的是原始温控寄存器值，而不是减 40 后的人类可读温度。
        val chars = charArrayOf(
            0x02.toChar(),
            0x10.toChar(),
            0x00.toChar(),
            0x00.toChar(),
            0x00.toChar(),
            0x01.toChar(),
            0x02.toChar(),
            (temperature shr 8).toChar(),
            (temperature and 0xFF).toChar(),
        )
        val crc = CRCCheck.CRC16(chars)
        sendCommand(FuncUtils.CharArrToHex(chars, crc))
    }

    private fun buildOpenDoorCommand(doorIndex: Int, openDoorDelay: Int = 500): String {
        // 串口协议按 0-based 门位拼包，但对外暴露时已经统一换算为 1-based。
        val chars = CharArray(7 + cabinetDoorCount * 2)
        chars[0] = (16 + OUT_DOOR_INDEX).toChar()
        chars[1] = 16.toChar()
        chars[2] = 0.toChar()
        chars[3] = 0.toChar()
        chars[4] = 0.toChar()
        chars[5] = cabinetDoorCount.toChar()
        chars[6] = (cabinetDoorCount * 2).toChar()
        for (index in 0 until cabinetDoorCount) {
            if (doorIndex == index) {
                chars[7 + 2 * index] = (openDoorDelay shr 8).toChar()
                chars[8 + 2 * index] = (openDoorDelay and 0xFF).toChar()
            } else {
                chars[7 + 2 * index] = 0.toChar()
                chars[8 + 2 * index] = 0.toChar()
            }
        }
        val crc = CRCCheck.CRC16(chars)
        return FuncUtils.CharArrToHex(chars, crc)
    }

    private fun markHeartbeat() {
        mutableHeartbeat.value = System.currentTimeMillis()
    }

    private fun PrintRequest.toRequestType(): PrintResultInfo.RequestType {
        return when (this) {
            is PrintRequest.Label -> PrintResultInfo.RequestType.LABEL
            is PrintRequest.RawBytes -> PrintResultInfo.RequestType.RAW_BYTES
        }
    }
}

/**
 * JW 串口协议的底层收包助手。
 *
 * 负责做分片拼接、CRC 校验和基础帧合法性过滤，只有在确定拿到完整有效帧后，
 * 才会把十六进制字符串继续抛给上层 driver 解析。
 */
internal class JwCabinetSerialHelper(
    private val onFrameReceived: (String) -> Unit,
) : SerialHelper() {

    private var receiveData = StringBuilder()

    fun open(port: String, baudRate: Int) {
        super.port = port
        super.baudRate = baudRate
        if (!isOpen) {
            open()
        }
    }

    override fun onDataReceived(data: ComBean?) {
        if (data == null) {
            return
        }
        receiveData.append(FuncUtils.ByteArrToHex(data.bRec))
        if (receiveData.length > 200) {
            // 串口粘包异常时直接丢弃缓存，避免后续一直在错误边界上滚动解析。
            receiveData = StringBuilder()
            return
        }
        val bytes = FuncUtils.HexToByteArr(receiveData.toString())
        if (bytes.isEmpty()) {
            return
        }
        val address = bytes[0].toInt() and 0xFF
        if (address !in setOf(1, 2, 6, 16, 17, 18, 19)) {
            receiveData = StringBuilder()
            return
        }
        if (bytes.size > 2) {
            val command = bytes[1].toInt()
            if (command != 3 && command != 16) {
                receiveData = StringBuilder()
                return
            }
            val payloadSize = if (command == 3) bytes[2].toInt() and 0xFF else 0
            if (FuncUtils.HexToInt(CRCCheckUtils.getCRC(bytes)) == 0) {
                if (command == 3 && bytes.size >= payloadSize + 5) {
                    onFrameReceived(receiveData.toString())
                    receiveData = StringBuilder()
                } else if (command == 16) {
                    // 0x10 写寄存器应答不需要继续上抛，上层只关心写成功后的后续轮询结果。
                    receiveData = StringBuilder()
                }
            } else if (bytes.size >= payloadSize + 5) {
                receiveData = StringBuilder()
            }
        }
    }

    fun closeSerial() {
        close()
        receiveData = StringBuilder()
    }
}
