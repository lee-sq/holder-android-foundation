package com.holderzone.hardware.cabinet.driver.star

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
import com.holderzone.hardware.cabinet.internal.spi.CabinetDriver
import com.holderzone.hardware.cabinet.internal.spi.CabinetDriverFactory
import com.holderzone.hardware.cabinet.print.CabinetLabelRenderer
import com.holderzone.hardware.cabinet.print.PrintResult
import com.holderzone.hardware.cabinet.print.impl.StarTSPLPrintProvide
import com.xingx.lock.grid.GridLockConfig
import com.xingx.lock.grid.GridLockController
import com.xingx.lock.grid.listener.OnGridLocksListener
import com.xingx.lock.grid.listener.OnGridTempListener
import com.xingx.lock.grid.listener.OnWeightingListener
import com.xingx.print.gy.PrintController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

class StarDriverFactory : CabinetDriverFactory {
    override val vendor: CabinetVendor = CabinetVendor.STAR

    override suspend fun open(
        appContext: Context,
        config: CabinetConfig,
        probeOnly: Boolean,
    ): CabinetResult<CabinetDriver> {
        return StarCabinetDriver.create(appContext, config, probeOnly)
    }
}

/**
 * Star 留样柜驱动实现。
 *
 * 该驱动主要包装 Star 厂商提供的门锁、温度、称重和打印控制器，
 * 对外统一转换成 Cabinet SDK 的能力模型与事件流。
 */
internal class StarCabinetDriver private constructor(
    private val appContext: Context,
    private val config: CabinetConfig,
    private val probeOnly: Boolean,
) : CabinetDriver {

    override val vendor: CabinetVendor = CabinetVendor.STAR

    override val capabilities: CabinetCapabilities = CabinetCapabilities(
        selectedVendor = vendor,
        openDoor = true,
        queryDoorState = true,
        readWeight = true,
        streamWeight = true,
        zeroScale = true,
        calibrateScale = false,
        printLabel = !probeOnly,
        printRawBytes = !probeOnly,
        readTemperature = true,
        setTargetTemperature = false,
    )

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

    private val printer = StarTSPLPrintProvide()
    private val doorStateMap = mutableMapOf<Int, Boolean>()
    private var lastWeightReading: WeightReading? = null
    private var lastTemperatureReading: TemperatureReading? = null

    // 监听器引用需要自己持有，便于 stop/close 时显式解除绑定。
    private var tempListener: OnGridTempListener? = null
    private var doorListener: OnGridLocksListener? = null
    private var weightListener: OnWeightingListener? = null

    companion object {
        private val DEFAULT_CABINET_PORT = SerialPortEndpoint("/dev/ttyS3", 115200)
        private val DEFAULT_PRINTER_PORT = SerialPortEndpoint("/dev/ttyS0", 115200)

        suspend fun create(
            appContext: Context,
            config: CabinetConfig,
            probeOnly: Boolean,
        ): CabinetResult<CabinetDriver> {
            val driver = StarCabinetDriver(appContext, config, probeOnly)
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
            // Star SDK 内部大量依赖单例监听器，关闭时必须先解绑再 stop，避免下次启动串状态。
            GridLockController.instance().setOnTempListener(null)
            GridLockController.instance().setOnGridLocksListener(null)
            GridLockController.instance().setOnWeightingListener(null)
            GridLockController.instance().stop()
            PrintController.instance().close()
            printer.disconnect()
            tempListener = null
            doorListener = null
            weightListener = null
            running.set(false)
            mutableHeartbeat.value = 0L
            CabinetResult.Ok(Unit)
        }.getOrElse { throwable ->
            CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = throwable.message ?: "Failed to stop Star driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun openDoors(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot> {
        if (!running.get()) {
            return CabinetResult.Err(CabinetError.DeviceUnavailable("Star driver is not running.", vendor))
        }
        val targets = request.doors.toSet()
        GridLockController.instance().openLocks(null, *targets.toIntArray())
        val deadline = System.currentTimeMillis() + request.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snapshot = snapshotFor(targets)
            if (snapshot.allRequestedOpened) {
                mutableDoorSnapshots.emit(snapshot)
                mutableEvents.emit(CabinetEvent.DoorOpened(snapshot))
                return CabinetResult.Ok(snapshot)
            }
            delay(200L)
        }
        return CabinetResult.Err(
            CabinetError.OperationFailed(
                message = "Timed out while opening Star doors $targets.",
                vendor = vendor,
            )
        )
    }

    override suspend fun queryDoorState(door: Int): CabinetResult<DoorStateSnapshot> {
        if (door <= 0) {
            return CabinetResult.Err(CabinetError.Configuration("door must be a 1-based positive number."))
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
        GridLockController.instance().peelZero()
        return CabinetResult.Ok(Unit)
    }

    override suspend fun calibrateScale(standardWeightGrams: Double): CabinetResult<Unit> {
        return CabinetResult.Err(
            CabinetError.Unsupported(
                message = "Star driver does not support scale calibration.",
                vendor = vendor,
            )
        )
    }

    override suspend fun print(request: PrintRequest): CabinetResult<PrintResultInfo> {
        if (probeOnly) {
            return CabinetResult.Err(CabinetError.Unsupported("Probe mode does not allow printing.", vendor))
        }
        val payload = when (request) {
            is PrintRequest.Label -> CabinetLabelRenderer.renderStarLabel(request)
            is PrintRequest.RawBytes -> request.bytes
        }
        return when (printer.printAndAwaitCompletion(payload)) {
            is PrintResult.Success -> CabinetResult.Ok(
                PrintResultInfo(
                    vendor = vendor,
                    requestType = request.toRequestType(),
                    byteCount = payload.size,
                )
            )

            is PrintResult.Failed -> CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = "Star print failed.",
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
                message = "Star driver does not support target temperature control.",
                vendor = vendor,
            )
        )
    }

    private suspend fun start(): CabinetResult<CabinetDriver> {
        return runCatching {
            val cabinetPort = config.ports.cabinet ?: DEFAULT_CABINET_PORT
            val printerPort = config.ports.printer ?: DEFAULT_PRINTER_PORT
            GridLockController.instance().config(
                GridLockConfig().apply {
                    path = cabinetPort.device
                    baudRate = cabinetPort.baudRate
                }
            )
            tempListener = object : OnGridTempListener {
                override fun onTemp(temp1: Float, temp2: Float) {
                    // Star 会回多路温度，首版统一暴露主温度通道 temp1。
                    val reading = TemperatureReading(celsius = temp1.toDouble())
                    markHeartbeat()
                    lastTemperatureReading = reading
                    mutableTemperatureReadings.tryEmit(reading)
                    mutableEvents.tryEmit(CabinetEvent.TemperatureUpdated(reading))
                }
            }
            doorListener = object : OnGridLocksListener {
                override fun onAllClose(allClose: Boolean, openLocks: MutableMap<Int, Boolean>) = Unit

                override fun onLockStatus(isChange: Boolean, lockStatus: MutableMap<Int, Boolean>) {
                    markHeartbeat()
                    if (!isChange) {
                        return
                    }
                    // Star SDK 自身就使用 1-based 门号，这里可以直接映射为对外语义。
                    lockStatus.forEach { entry ->
                        doorStateMap[entry.key] = entry.value
                    }
                    val snapshot = snapshotFor(doorStateMap.keys)
                    mutableDoorSnapshots.tryEmit(snapshot)
                    mutableEvents.tryEmit(CabinetEvent.DoorSnapshotChanged(snapshot))
                }
            }
            weightListener = object : OnWeightingListener {
                override fun onError(msg: String) = Unit

                override fun onWeighting(weight: Double) {
                    val reading = WeightReading(grams = weight)
                    markHeartbeat()
                    lastWeightReading = reading
                    mutableWeightReadings.tryEmit(reading)
                    mutableEvents.tryEmit(CabinetEvent.WeightUpdated(reading))
                }
            }
            GridLockController.instance().setOnTempListener(tempListener)
            GridLockController.instance().setOnGridLocksListener(doorListener)
            GridLockController.instance().setOnWeightingListener(weightListener)
            GridLockController.instance().start()
            if (!probeOnly) {
                printer.connect(printerPort.device, printerPort.baudRate)
            }
            running.set(true)
            CabinetResult.Ok(this as CabinetDriver)
        }.getOrElse { throwable ->
            close()
            CabinetResult.Err(
                CabinetError.DeviceUnavailable(
                    message = throwable.message ?: "Failed to initialize Star driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private fun markHeartbeat() {
        mutableHeartbeat.value = System.currentTimeMillis()
    }

    private fun snapshotFor(requestedDoors: Set<Int>): DoorStateSnapshot {
        val openedDoors = requestedDoors.filterTo(linkedSetOf()) { doorStateMap[it] == true }
        return DoorStateSnapshot(
            requestedDoors = requestedDoors,
            openedDoors = openedDoors,
        )
    }

    private fun PrintRequest.toRequestType(): PrintResultInfo.RequestType {
        return when (this) {
            is PrintRequest.Label -> PrintResultInfo.RequestType.LABEL
            is PrintRequest.RawBytes -> PrintResultInfo.RequestType.RAW_BYTES
        }
    }
}
