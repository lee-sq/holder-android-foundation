package com.holderzone.hardware.cabinet.driver.jw.sdk

import android.content.Context
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
import com.tongdeliu.jwbaselib.SampleDeviceManager
import com.tongdeliu.jwbaselib.callback.SampleDeviceCallBack
import com.tongdeliu.jwbaselib.json.DeviceBatteryInfo
import com.tongdeliu.jwbaselib.json.DeviceCabinetInfo
import com.tongdeliu.jwbaselib.json.DeviceOtherInfo
import com.tongdeliu.jwbaselib.json.DeviceWeightInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class JwSdkDriverFactory : CabinetDriverFactory {
    override val vendor: CabinetVendor = CabinetVendor.JW_SDK

    override suspend fun open(
        appContext: Context,
        config: CabinetConfig,
        probeOnly: Boolean,
    ): CabinetResult<CabinetDriver> {
        return JwSdkCabinetDriver.create(appContext, config, probeOnly)
    }
}

/**
 * 基于 JW 官方 SDK 的柜机驱动实现。
 *
 * 这一版优先复用厂商现成能力，由 SDK 负责门锁、重量、温度等状态回调，
 * 本驱动只做能力适配、结果转换和资源释放收口。
 */
internal class JwSdkCabinetDriver private constructor(
    private val appContext: Context,
    private val config: CabinetConfig,
    private val probeOnly: Boolean,
) : CabinetDriver {

    override val vendor: CabinetVendor = CabinetVendor.JW_SDK

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
        setTargetTemperature = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

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

    private val doorCount = config.doorCount ?: DEFAULT_DOOR_COUNT
    private val printer = CommonTSPLPrintProvide()

    // 厂商 SDK 是单例入口，这里只缓存当前会话真正使用到的 manager 引用。
    private var deviceManager: SampleDeviceManager? = null
    private val doorStateMap = mutableMapOf<Int, Boolean>()
    private var lastWeightReading: WeightReading? = null
    private var lastRawWeight: Double? = null
    private var lastTemperatureReading: TemperatureReading? = null
    private var currentSlope = 1.0

    companion object {
        private val DEFAULT_CABINET_PORT = SerialPortEndpoint("/dev/ttyS2", 9600)
        private val DEFAULT_PRINTER_PORT = SerialPortEndpoint("/dev/ttyS3", 9600)
        private const val DEFAULT_DOOR_COUNT = 6

        suspend fun create(
            appContext: Context,
            config: CabinetConfig,
            probeOnly: Boolean,
        ): CabinetResult<CabinetDriver> {
            val driver = JwSdkCabinetDriver(appContext, config, probeOnly)
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
            // 打印串口和厂商 SDK 回调都属于当前 driver 持有的资源，关闭时必须一起释放。
            printer.disconnect()
            val manager = deviceManager
            deviceManager = null
            manager?.setSampleDeviceCallBack(null)
            manager?.closeSampleManager()
            running.set(false)
            mutableHeartbeat.value = 0L
            scope.coroutineContext.cancelChildren()
            CabinetResult.Ok(Unit)
        }.getOrElse { throwable ->
            CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = throwable.message ?: "Failed to stop JW SDK driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun openDoors(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot> {
        if (!running.get()) {
            return CabinetResult.Err(CabinetError.DeviceUnavailable("JW SDK driver is not running.", vendor))
        }
        val manager = deviceManager
            ?: return CabinetResult.Err(CabinetError.DeviceUnavailable("JW SDK device manager is unavailable.", vendor))
        val targets = request.doors.toSet()
        if (targets.any { it > doorCount }) {
            return CabinetResult.Err(
                CabinetError.Configuration("Door index exceeds configured doorCount=$doorCount.")
            )
        }
        targets.forEach { door -> manager.openLightAndLock(0, door - 1) }
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
                // 对尚未打开的门做重试，兼容个别设备第一次发指令丢失的情况。
                targets.filterNot { doorStateMap[it] == true }.forEach { door ->
                    manager.openLightAndLock(0, door - 1)
                }
                lastRetry = now
            }
            delay(200L)
        }
        return CabinetResult.Err(
            CabinetError.OperationFailed(
                message = "Timed out while opening doors $targets.",
                vendor = vendor,
            )
        )
    }

    override suspend fun queryDoorState(door: Int): CabinetResult<DoorStateSnapshot> {
        if (door !in 1..doorCount) {
            return CabinetResult.Err(
                CabinetError.Configuration("door must be within 1..$doorCount for vendor $vendor.")
            )
        }
        return CabinetResult.Ok(snapshotFor(setOf(door)))
    }

    override suspend fun readWeight(): CabinetResult<WeightReading> {
        val reading = lastWeightReading
        return if (reading != null) {
            CabinetResult.Ok(reading)
        } else {
            CabinetResult.Err(
                CabinetError.DeviceUnavailable(
                    message = "No weight reading is available yet.",
                    vendor = vendor,
                )
            )
        }
    }

    override suspend fun zeroScale(): CabinetResult<Unit> {
        val manager = deviceManager
            ?: return CabinetResult.Err(CabinetError.DeviceUnavailable("JW SDK device manager is unavailable.", vendor))
        manager.clearZero()
        return CabinetResult.Ok(Unit)
    }

    override suspend fun calibrateScale(standardWeightGrams: Double): CabinetResult<Unit> {
        val rawWeight = lastRawWeight
        val manager = deviceManager
            ?: return CabinetResult.Err(CabinetError.DeviceUnavailable("JW SDK device manager is unavailable.", vendor))
        if (rawWeight == null || rawWeight <= 0.0) {
            return CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = "A positive raw weight reading is required before calibration.",
                    vendor = vendor,
                )
            )
        }
        val slope = standardWeightGrams / rawWeight
        if (!slope.isFinite() || slope <= 0.0) {
            return CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = "Calculated calibration slope is invalid.",
                    vendor = vendor,
                )
            )
        }
        currentSlope = slope
        // 标定斜率由 SDK 外部注入的 CalibrationStore 持久化，避免和具体存储方案耦合。
        config.calibrationStore.writeWeightSlope(vendor, slope)
        manager.setWeightCalibration(slope.toFloat())
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
                    message = "JW SDK print failed.",
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
            CabinetResult.Err(
                CabinetError.DeviceUnavailable(
                    message = "No temperature reading is available yet.",
                    vendor = vendor,
                )
            )
        }
    }

    override suspend fun setTargetTemperature(celsius: Double): CabinetResult<Unit> {
        return CabinetResult.Err(
            CabinetError.Unsupported(
                message = "JW SDK does not support target temperature control.",
                vendor = vendor,
            )
        )
    }

    private suspend fun start(): CabinetResult<CabinetDriver> {
        return runCatching {
            currentSlope = config.calibrationStore.readWeightSlope(vendor) ?: 1.0
            val cabinetPort = config.ports.cabinet ?: DEFAULT_CABINET_PORT
            val printerPort = config.ports.printer ?: DEFAULT_PRINTER_PORT
            val manager = SampleDeviceManager.getInstance()
            deviceManager = manager
            manager.initCabinetInfo(1, doorCount)
            manager.changeVersionCode(1)
            manager.initSerialPort(cabinetPort.device, cabinetPort.baudRate)
            manager.setWeightCalibration(currentSlope.toFloat())
            manager.setSampleDeviceCallBack(CabinetCallback())
            if (!probeOnly) {
                printer.connect(printerPort.device, printerPort.baudRate)
            }
            scope.launch {
                // 厂商 SDK 刚初始化完成时立即 startRead 会出现偶发不稳定，
                // 因此这里保守延迟一小段时间再启动轮询。
                delay(2_500L)
                withContext(Dispatchers.Main.immediate) {
                    manager.startRead()
                }
            }
            running.set(true)
            CabinetResult.Ok(this as CabinetDriver)
        }.getOrElse { throwable ->
            close()
            CabinetResult.Err(
                CabinetError.DeviceUnavailable(
                    message = throwable.message ?: "Failed to initialize JW SDK driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private suspend fun publishDoorSnapshot() {
        val snapshot = snapshotFor(doorStateMap.keys)
        mutableDoorSnapshots.emit(snapshot)
        mutableEvents.emit(CabinetEvent.DoorSnapshotChanged(snapshot))
    }

    private fun snapshotFor(requestedDoors: Set<Int>): DoorStateSnapshot {
        val openedDoors = requestedDoors.filterTo(linkedSetOf()) { doorStateMap[it] == true }
        return DoorStateSnapshot(
            requestedDoors = requestedDoors,
            openedDoors = openedDoors,
        )
    }

    private suspend fun publishWeight(reading: WeightReading) {
        lastWeightReading = reading
        mutableWeightReadings.emit(reading)
        mutableEvents.emit(CabinetEvent.WeightUpdated(reading))
    }

    private suspend fun publishTemperature(reading: TemperatureReading) {
        lastTemperatureReading = reading
        mutableTemperatureReadings.emit(reading)
        mutableEvents.emit(CabinetEvent.TemperatureUpdated(reading))
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

    private inner class CabinetCallback : SampleDeviceCallbackAdapter() {
        override fun onWeightInfoCallBack(deviceWeightInfo: DeviceWeightInfo?) {
            if (deviceWeightInfo == null) {
                return
            }
            markHeartbeat()
            val rawWeightGrams = kilogramsToGrams(deviceWeightInfo.weight_real_value.toDouble())
            lastRawWeight = rawWeightGrams
            // JW SDK 回来的原始单位是 kg，这里统一换算成 g 后再进入公共模型和标定流程。
            val reading = WeightReading(
                grams = rawWeightGrams,
                rawGrams = rawWeightGrams,
                slope = currentSlope,
            )
            scope.launch {
                publishWeight(reading)
            }
        }

        override fun onCabinetInfoCallBack(deviceCabinetInfo: DeviceCabinetInfo?) {
            if (deviceCabinetInfo == null) {
                return
            }
            markHeartbeat()
            // 对外统一改为 1-based 门号，所以这里在接厂商数组时统一做 +1。
            deviceCabinetInfo.door_lights.forEachIndexed { index, _ ->
                doorStateMap[index + 1] = deviceCabinetInfo.isOpen(index)
            }
            val reading = TemperatureReading(
                celsius = deviceCabinetInfo.temperature.toDouble(),
            )
            scope.launch {
                publishDoorSnapshot()
                publishTemperature(reading)
            }
        }

        override fun onOtherInfoCallBack(deviceOtherInfo: DeviceOtherInfo?) {
            if (deviceOtherInfo == null) {
                return
            }
            markHeartbeat()
            val manager = deviceManager ?: return
            // 这里保留原厂行为：冰柜门打开时自动补开灯，关闭时自动关灯。
            if (deviceOtherInfo.isIce_door_state && !deviceOtherInfo.isIce_light_state) {
                manager.openOrCloseLight()
                manager.sendHex("02100005000102AAAA4C2A")
            } else if (!deviceOtherInfo.isIce_door_state && deviceOtherInfo.isIce_light_state) {
                manager.sendHex("021000050001020000B2F5")
            }
        }
    }
}

/**
 * 对厂商回调做空实现适配，避免每个驱动回调都必须覆盖无关方法。
 */
internal abstract class SampleDeviceCallbackAdapter : SampleDeviceCallBack {
    override fun onWeightInfoCallBack(deviceWeightInfo: DeviceWeightInfo?) = Unit

    override fun onBatteryInCallBack(deviceBatteryInfo: DeviceBatteryInfo?) = Unit

    override fun onCabinetInfoCallBack(deviceCabinetInfo: DeviceCabinetInfo?) = Unit

    override fun onOtherInfoCallBack(deviceOtherInfo: DeviceOtherInfo?) = Unit

    override fun onRFIDCallBack(RFID: String?) = Unit

    override fun onQRCodeCallBack(QRCode: String?) = Unit

    override fun onControlSuccessCallBack(callStr: String?) = Unit

    override fun onErrorCallBack(callStr: String?) = Unit
}
