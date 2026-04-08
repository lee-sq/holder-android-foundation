package com.holderzone.hardware.cabinet.core

import android.content.Context
import android.os.Looper
import com.holderzone.hardware.cabinet.CabinetCapabilities
import com.holderzone.hardware.cabinet.CabinetConfig
import com.holderzone.hardware.cabinet.CabinetError
import com.holderzone.hardware.cabinet.CabinetEvent
import com.holderzone.hardware.cabinet.CabinetFacade
import com.holderzone.hardware.cabinet.CabinetResult
import com.holderzone.hardware.cabinet.CabinetState
import com.holderzone.hardware.cabinet.CabinetVendor
import com.holderzone.hardware.cabinet.CabinetVendorPreference
import com.holderzone.hardware.cabinet.DoorController
import com.holderzone.hardware.cabinet.DoorOpenRequest
import com.holderzone.hardware.cabinet.DoorStateSnapshot
import com.holderzone.hardware.cabinet.PrintRequest
import com.holderzone.hardware.cabinet.PrintResultInfo
import com.holderzone.hardware.cabinet.PrinterController
import com.holderzone.hardware.cabinet.ScaleController
import com.holderzone.hardware.cabinet.TemperatureController
import com.holderzone.hardware.cabinet.TemperatureReading
import com.holderzone.hardware.cabinet.WeightReading
import com.holderzone.hardware.cabinet.internal.spi.CabinetDriver
import com.holderzone.hardware.cabinet.internal.spi.CabinetDriverFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cabinet SDK 默认门面实现。
 *
 * 这个实现的核心目标是把所有生命周期命令和硬件命令都串行化处理，
 * 避免 start / stop / recover / openDoor / print 等操作并发交错后把底层驱动状态打乱。
 * 因此它内部使用了一个单通道命令队列，所有操作都在同一条执行链路上顺序完成。
 */
class DefaultCabinetFacade(
    context: Context,
    private val driverFactories: List<CabinetDriverFactory>,
) : CabinetFacade {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // 所有 facade 命令都必须先进入这一条通道，保证生命周期和硬件动作串行化。
    private val commandChannel = Channel<FacadeCommand>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)

    private val mutableState = MutableStateFlow<CabinetState>(CabinetState.Idle)
    private val mutableCapabilities = MutableStateFlow(CabinetCapabilities.NONE)
    private val mutableEvents = MutableSharedFlow<CabinetEvent>(extraBufferCapacity = 64)
    private val mutableDoorSnapshots = MutableSharedFlow<DoorStateSnapshot>(replay = 1, extraBufferCapacity = 16)
    private val mutableWeightReadings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 16)
    private val mutableTemperatureReadings = MutableSharedFlow<TemperatureReading>(replay = 1, extraBufferCapacity = 16)

    private var currentConfig: CabinetConfig? = null
    private var activeDriver: CabinetDriver? = null
    private var activeFactory: CabinetDriverFactory? = null
    private var activeVendor: CabinetVendor? = null
    private var bridgeJobs: List<Job> = emptyList()
    private var heartbeatJob: Job? = null
    private var restartFailureCount = 0

    override val state: StateFlow<CabinetState> = mutableState.asStateFlow()
    override val events: Flow<CabinetEvent> = mutableEvents.asSharedFlow()
    override val capabilities: StateFlow<CabinetCapabilities> = mutableCapabilities.asStateFlow()

    override val door: DoorController = DoorControllerImpl()
    override val scale: ScaleController = ScaleControllerImpl()
    override val printer: PrinterController = PrinterControllerImpl()
    override val temperature: TemperatureController = TemperatureControllerImpl()

    init {
        scope.launch {
            // 统一命令循环。任何对驱动状态有影响的操作都只在这里处理。
            for (command in commandChannel) {
                when (command) {
                    is FacadeCommand.Start -> command.complete(handleStart(command.config))
                    is FacadeCommand.Stop -> command.complete(handleStop())
                    is FacadeCommand.OpenDoor -> command.complete(handleOpenDoors(command.request))
                    is FacadeCommand.QueryDoor -> command.complete(handleQueryDoor(command.door))
                    is FacadeCommand.ReadWeight -> command.complete(handleReadWeight())
                    is FacadeCommand.ZeroScale -> command.complete(handleZeroScale())
                    is FacadeCommand.CalibrateScale -> command.complete(handleCalibrateScale(command.standardWeightGrams))
                    is FacadeCommand.Print -> command.complete(handlePrint(command.request))
                    is FacadeCommand.ReadTemperature -> command.complete(handleReadTemperature())
                    is FacadeCommand.SetTargetTemperature -> command.complete(handleSetTargetTemperature(command.celsius))
                    is FacadeCommand.Recover -> command.complete(handleRecover(command.reason))
                    is FacadeCommand.Close -> {
                        command.complete(handleCloseInternal())
                        break
                    }
                }
            }
        }
    }

    override suspend fun start(config: CabinetConfig): CabinetResult<Unit> {
        return submit(FacadeCommand.Start(config))
    }

    override suspend fun stop(): CabinetResult<Unit> {
        return submit(FacadeCommand.Stop())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        val deferred = CompletableDeferred<CabinetResult<Unit>>()
        commandChannel.trySend(FacadeCommand.Close(deferred))
        commandChannel.close()

        if (!hasMainLooper() || isMainThread()) {
            scope.launch {
                deferred.await()
                scope.cancel()
            }
        } else {
            runBlocking {
                deferred.await()
            }
            scope.cancel()
        }
    }

    private suspend fun handleStart(config: CabinetConfig): CabinetResult<Unit> {
        ensureOpen()
        val current = activeDriver
        // 相同配置下重复 start 直接视为幂等成功，避免页面重入时做无意义重建。
        if (current != null && current.isRunning() && currentConfig == config) {
            return CabinetResult.Ok(Unit)
        }

        mutableState.value = CabinetState.Starting(config)
        currentConfig = config
        restartFailureCount = 0

        return when (val result = resolveAndOpenDriver(config)) {
            is CabinetResult.Ok -> {
                startHeartbeatMonitor(config)
                CabinetResult.Ok(Unit)
            }

            is CabinetResult.Err -> {
                mutableState.value = CabinetState.Error(activeVendor, result.error)
                emitEvent(CabinetEvent.Error(result.error))
                result
            }
        }
    }

    private suspend fun handleStop(): CabinetResult<Unit> {
        ensureOpen()
        stopHeartbeatMonitor()
        val stopResult = releaseActiveDriver()
        mutableCapabilities.value = CabinetCapabilities.NONE
        mutableState.value = CabinetState.Stopped(activeVendor)
        currentConfig = null
        activeFactory = null
        activeVendor = null
        restartFailureCount = 0
        return stopResult
    }

    private suspend fun handleOpenDoors(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot> {
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        val result = driver.openDoors(request)
        if (result is CabinetResult.Ok) {
            mutableDoorSnapshots.emit(result.value)
            emitEvent(CabinetEvent.DoorOpened(result.value))
        }
        return result
    }

    private suspend fun handleQueryDoor(door: Int): CabinetResult<DoorStateSnapshot> {
        if (door <= 0) {
            return CabinetResult.Err(CabinetError.Configuration("door must be a 1-based positive number."))
        }
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        return driver.queryDoorState(door)
    }

    private suspend fun handleReadWeight(): CabinetResult<WeightReading> {
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        return driver.readWeight()
    }

    private suspend fun handleZeroScale(): CabinetResult<Unit> {
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        if (!driver.capabilities.zeroScale) {
            return CabinetResult.Err(
                CabinetError.Unsupported(
                    message = "Vendor ${driver.vendor} does not support zeroScale.",
                    vendor = driver.vendor,
                )
            )
        }
        return driver.zeroScale()
    }

    private suspend fun handleCalibrateScale(standardWeightGrams: Double): CabinetResult<Unit> {
        if (standardWeightGrams <= 0) {
            return CabinetResult.Err(CabinetError.Configuration("standardWeightGrams must be greater than 0."))
        }
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        if (!driver.capabilities.calibrateScale) {
            return CabinetResult.Err(
                CabinetError.Unsupported(
                    message = "Vendor ${driver.vendor} does not support calibration.",
                    vendor = driver.vendor,
                )
            )
        }
        return driver.calibrateScale(standardWeightGrams)
    }

    private suspend fun handlePrint(request: PrintRequest): CabinetResult<PrintResultInfo> {
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        val supported = when (request) {
            is PrintRequest.Label -> driver.capabilities.printLabel
            is PrintRequest.RawBytes -> driver.capabilities.printRawBytes
        }
        if (!supported) {
            return CabinetResult.Err(
                CabinetError.Unsupported(
                    message = "Vendor ${driver.vendor} does not support this print request.",
                    vendor = driver.vendor,
                )
            )
        }
        return driver.print(request)
    }

    private suspend fun handleReadTemperature(): CabinetResult<TemperatureReading> {
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        return driver.readTemperature()
    }

    private suspend fun handleSetTargetTemperature(celsius: Double): CabinetResult<Unit> {
        val driver = requireDriver() ?: return CabinetResult.Err(driverMissingError())
        if (!driver.capabilities.setTargetTemperature) {
            return CabinetResult.Err(
                CabinetError.Unsupported(
                    message = "Vendor ${driver.vendor} does not support target temperature control.",
                    vendor = driver.vendor,
                )
            )
        }
        return driver.setTargetTemperature(celsius)
    }

    private suspend fun handleRecover(reason: String): CabinetResult<Unit> {
        val config = currentConfig ?: return CabinetResult.Err(driverMissingError())
        val vendor = activeVendor ?: return CabinetResult.Err(driverMissingError())
        mutableState.value = CabinetState.Recovering(vendor, restartFailureCount + 1)
        emitEvent(CabinetEvent.Info("Cabinet heartbeat recovery triggered: $reason"))

        val currentFactory = activeFactory
        // 优先尝试用当前厂商做原地重启，避免每次恢复都重新全量探测。
        val restartResult = if (currentFactory != null) {
            restartWithFactory(currentFactory, config)
        } else {
            CabinetResult.Err(CabinetError.DeviceUnavailable("No active cabinet factory is available."))
        }
        if (restartResult is CabinetResult.Ok) {
            restartFailureCount = 0
            return CabinetResult.Ok(Unit)
        }

        restartFailureCount += 1
        if (restartFailureCount >= config.heartbeat.maxRestartFailures) {
            restartFailureCount = 0
            releaseActiveDriver()
            // 当前厂商连续恢复失败后，回退到完整探测流程，让其他厂商驱动有机会接管。
            return when (val fallback = resolveAndOpenDriver(config)) {
                is CabinetResult.Ok -> CabinetResult.Ok(Unit)
                is CabinetResult.Err -> {
                    mutableState.value = CabinetState.Error(activeVendor, fallback.error)
                    emitEvent(CabinetEvent.Error(fallback.error))
                    fallback
                }
            }
        }

        val error = (restartResult as CabinetResult.Err).error
        mutableState.value = CabinetState.Error(activeVendor, error)
        emitEvent(CabinetEvent.Error(error))
        return CabinetResult.Err(error)
    }

    private suspend fun handleCloseInternal(): CabinetResult<Unit> {
        stopHeartbeatMonitor()
        val result = releaseActiveDriver()
        mutableCapabilities.value = CabinetCapabilities.NONE
        mutableState.value = CabinetState.Closed
        currentConfig = null
        activeFactory = null
        activeVendor = null
        return result
    }

    private suspend fun resolveAndOpenDriver(config: CabinetConfig): CabinetResult<Unit> {
        val candidates = orderedFactories(config.vendorPreference)
        if (candidates.isEmpty()) {
            return CabinetResult.Err(CabinetError.Configuration("No cabinet driver is registered."))
        }

        val errors = mutableListOf<String>()
        for (factory in candidates) {
            // 先以 probeOnly 做轻量探测，避免把所有厂商都完整初始化一遍。
            val probeResult = probeFactory(factory, config)
            if (probeResult is CabinetResult.Err) {
                errors += "${factory.vendor}: ${probeResult.error.message}"
                if (config.vendorPreference is CabinetVendorPreference.Explicit) {
                    return probeResult
                }
                continue
            }

            val openResult = factory.open(appContext, config, probeOnly = false)
            when (openResult) {
                is CabinetResult.Ok -> {
                    attachDriver(factory, openResult.value)
                    emitEvent(CabinetEvent.VendorSelected(factory.vendor))
                    emitEvent(CabinetEvent.Info("Cabinet driver selected: ${factory.vendor}"))
                    mutableState.value = CabinetState.Running(factory.vendor, openResult.value.capabilities)
                    return CabinetResult.Ok(Unit)
                }

                is CabinetResult.Err -> {
                    errors += "${factory.vendor}: ${openResult.error.message}"
                    if (config.vendorPreference is CabinetVendorPreference.Explicit) {
                        return openResult
                    }
                }
            }
        }

        return CabinetResult.Err(
            CabinetError.DeviceUnavailable(
                message = buildString {
                    append("Unable to resolve a cabinet driver.")
                    if (errors.isNotEmpty()) {
                        append(' ')
                        append(errors.joinToString(separator = " | "))
                    }
                }
            )
        )
    }

    private suspend fun probeFactory(
        factory: CabinetDriverFactory,
        config: CabinetConfig,
    ): CabinetResult<Unit> {
        val probeResult = factory.open(appContext, config, probeOnly = true)
        if (probeResult is CabinetResult.Err) {
            return probeResult
        }
        val driver = (probeResult as CabinetResult.Ok).value
        return try {
            val initialBeat = driver.lastHeartbeatAtMs.value
            val deadline = System.currentTimeMillis() + config.probeTimeoutMs
            // 这里不要求 probe 驱动完成全部业务初始化，只要求它在限定时间内给出心跳，
            // 以证明对应厂商实现至少在当前机器上是“活的”。
            while (System.currentTimeMillis() < deadline) {
                if (driver.lastHeartbeatAtMs.value > initialBeat) {
                    return CabinetResult.Ok(Unit)
                }
                delay(20L)
            }
            CabinetResult.Err(
                CabinetError.DeviceUnavailable(
                    message = "Probe timeout for ${factory.vendor}.",
                    vendor = factory.vendor,
                )
            )
        } finally {
            runCatching { driver.close() }
        }
    }

    private fun orderedFactories(preference: CabinetVendorPreference): List<CabinetDriverFactory> {
        return when (preference) {
            is CabinetVendorPreference.Auto -> {
                // 自动探测顺序是有明确业务偏好的，不按注入顺序。
                val order = listOf(CabinetVendor.STAR, CabinetVendor.JW_SDK, CabinetVendor.JW_SERIAL)
                order.mapNotNull { vendor -> driverFactories.firstOrNull { it.vendor == vendor } }
            }

            is CabinetVendorPreference.Explicit -> {
                listOfNotNull(driverFactories.firstOrNull { it.vendor == preference.vendor })
            }
        }
    }

    private suspend fun restartWithFactory(
        factory: CabinetDriverFactory,
        config: CabinetConfig,
    ): CabinetResult<Unit> {
        val oldDriver = activeDriver
        // 先完整释放旧驱动，再拉起新实例，避免串口、监听器或 SDK 单例残留。
        releaseActiveDriver()
        val openResult = factory.open(appContext, config, probeOnly = false)
        return when (openResult) {
            is CabinetResult.Ok -> {
                attachDriver(factory, openResult.value)
                mutableState.value = CabinetState.Running(factory.vendor, openResult.value.capabilities)
                CabinetResult.Ok(Unit)
            }

            is CabinetResult.Err -> {
                runCatching { oldDriver?.close() }
                openResult
            }
        }
    }

    private fun attachDriver(
        factory: CabinetDriverFactory,
        driver: CabinetDriver,
    ) {
        activeFactory = factory
        activeDriver = driver
        activeVendor = driver.vendor
        mutableCapabilities.value = driver.capabilities
        bridgeJobs.forEach(Job::cancel)
        // bridgeJobs 负责把 driver 内部流桥接到 facade 对外暴露的统一流，
        // 同时在需要时更新 facade 自己的生命周期状态。
        bridgeJobs = listOf(
            scope.launch {
                driver.events.collect { event ->
                    when (event) {
                        is CabinetEvent.DoorSnapshotChanged -> mutableDoorSnapshots.emit(event.snapshot)
                        is CabinetEvent.DoorOpened -> mutableDoorSnapshots.emit(event.snapshot)
                        is CabinetEvent.WeightUpdated -> mutableWeightReadings.emit(event.reading)
                        is CabinetEvent.TemperatureUpdated -> mutableTemperatureReadings.emit(event.reading)
                        is CabinetEvent.Error -> mutableState.value = CabinetState.Error(driver.vendor, event.error)
                        else -> Unit
                    }
                    mutableEvents.emit(event)
                }
            },
            scope.launch {
                driver.doorSnapshots.collect { snapshot ->
                    mutableDoorSnapshots.emit(snapshot)
                }
            },
            scope.launch {
                driver.weightReadings.collect { reading ->
                    mutableWeightReadings.emit(reading)
                }
            },
            scope.launch {
                driver.temperatureReadings.collect { reading ->
                    mutableTemperatureReadings.emit(reading)
                }
            },
        )
    }

    private suspend fun releaseActiveDriver(): CabinetResult<Unit> {
        stopHeartbeatMonitor()
        bridgeJobs.forEach(Job::cancel)
        bridgeJobs = emptyList()

        val driver = activeDriver
        activeDriver = null
        if (driver == null) {
            return CabinetResult.Ok(Unit)
        }

        return try {
            // stop 负责业务级停止，close 负责底层资源兜底释放，两者都要走。
            val stopResult = driver.stop()
            runCatching { driver.close() }
            stopResult
        } catch (exception: Throwable) {
            CabinetResult.Err(
                CabinetError.OperationFailed(
                    message = exception.message ?: "Failed to stop cabinet driver.",
                    vendor = activeVendor,
                    cause = exception,
                )
            )
        }
    }

    private fun startHeartbeatMonitor(config: CabinetConfig) {
        stopHeartbeatMonitor()
        heartbeatJob = scope.launch {
            // 部分厂商 SDK 启动后需要一段预热时间，过早检查会把正常启动误判为超时。
            delay(config.heartbeat.startGraceMs)
            while (!closed.get()) {
                delay(config.heartbeat.intervalMs)
                val driver = activeDriver ?: continue
                val beat = driver.lastHeartbeatAtMs.value
                if (beat <= 0L) {
                    continue
                }
                val now = System.currentTimeMillis()
                if (now - beat > config.heartbeat.timeoutMs) {
                    // 恢复动作同样走命令通道，避免与其他操作并发踩状态。
                    commandChannel.trySend(FacadeCommand.Recover("Heartbeat timeout on ${driver.vendor}."))
                    delay(config.heartbeat.restartCooldownMs)
                }
            }
        }
    }

    private fun stopHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun emitEvent(event: CabinetEvent) {
        mutableEvents.emit(event)
    }

    private fun requireDriver(): CabinetDriver? {
        ensureOpen()
        return activeDriver
    }

    private fun driverMissingError(): CabinetError {
        return CabinetError.Configuration("CabinetFacade has not been started yet.")
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw CabinetError.Closed()
        }
    }

    private fun isMainThread(): Boolean {
        return try {
            Looper.myLooper() == Looper.getMainLooper()
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasMainLooper(): Boolean {
        return try {
            Looper.getMainLooper() != null
        } catch (_: Throwable) {
            false
        }
    }

    private sealed interface FacadeCommand {
        val completion: CompletableDeferred<*>

        /**
         * facade 内部命令模型。
         *
         * 所有外部调用都会先被包装成命令，再进入单通道顺序执行。
         */
        data class Start(
            val config: CabinetConfig,
            override val completion: CompletableDeferred<CabinetResult<Unit>> = CompletableDeferred(),
        ) : FacadeCommand

        data class Stop(
            override val completion: CompletableDeferred<CabinetResult<Unit>> = CompletableDeferred(),
        ) : FacadeCommand

        data class OpenDoor(
            val request: DoorOpenRequest,
            override val completion: CompletableDeferred<CabinetResult<DoorStateSnapshot>> = CompletableDeferred(),
        ) : FacadeCommand

        data class QueryDoor(
            val door: Int,
            override val completion: CompletableDeferred<CabinetResult<DoorStateSnapshot>> = CompletableDeferred(),
        ) : FacadeCommand

        data class ReadWeight(
            override val completion: CompletableDeferred<CabinetResult<WeightReading>> = CompletableDeferred(),
        ) : FacadeCommand

        data class ZeroScale(
            override val completion: CompletableDeferred<CabinetResult<Unit>> = CompletableDeferred(),
        ) : FacadeCommand

        data class CalibrateScale(
            val standardWeightGrams: Double,
            override val completion: CompletableDeferred<CabinetResult<Unit>> = CompletableDeferred(),
        ) : FacadeCommand

        data class Print(
            val request: PrintRequest,
            override val completion: CompletableDeferred<CabinetResult<PrintResultInfo>> = CompletableDeferred(),
        ) : FacadeCommand

        data class ReadTemperature(
            override val completion: CompletableDeferred<CabinetResult<TemperatureReading>> = CompletableDeferred(),
        ) : FacadeCommand

        data class SetTargetTemperature(
            val celsius: Double,
            override val completion: CompletableDeferred<CabinetResult<Unit>> = CompletableDeferred(),
        ) : FacadeCommand

        data class Recover(
            val reason: String,
            override val completion: CompletableDeferred<CabinetResult<Unit>> = CompletableDeferred(),
        ) : FacadeCommand

        data class Close(
            override val completion: CompletableDeferred<CabinetResult<Unit>>,
        ) : FacadeCommand
    }

    private suspend fun <T> submit(command: FacadeCommand): T {
        ensureOpen()
        commandChannel.send(command)
        @Suppress("UNCHECKED_CAST")
        return (command.completion as CompletableDeferred<T>).await()
    }

    private fun <T> FacadeCommand.complete(result: T) {
        @Suppress("UNCHECKED_CAST")
        (completion as CompletableDeferred<T>).complete(result)
    }

    private inner class DoorControllerImpl : DoorController {
        override val states: Flow<DoorStateSnapshot> = mutableDoorSnapshots.asSharedFlow()

        override suspend fun open(request: DoorOpenRequest): CabinetResult<DoorStateSnapshot> {
            return submit(FacadeCommand.OpenDoor(request))
        }

        override suspend fun query(door: Int): CabinetResult<DoorStateSnapshot> {
            return submit(FacadeCommand.QueryDoor(door))
        }
    }

    private inner class ScaleControllerImpl : ScaleController {
        override val readings: Flow<WeightReading> = mutableWeightReadings.asSharedFlow()

        override suspend fun read(): CabinetResult<WeightReading> {
            return submit(FacadeCommand.ReadWeight())
        }

        override suspend fun zero(): CabinetResult<Unit> {
            return submit(FacadeCommand.ZeroScale())
        }

        override suspend fun calibrate(standardWeightGrams: Double): CabinetResult<Unit> {
            return submit(FacadeCommand.CalibrateScale(standardWeightGrams))
        }
    }

    private inner class PrinterControllerImpl : PrinterController {
        override suspend fun print(request: PrintRequest): CabinetResult<PrintResultInfo> {
            return submit(FacadeCommand.Print(request))
        }
    }

    private inner class TemperatureControllerImpl : TemperatureController {
        override val readings: Flow<TemperatureReading> = mutableTemperatureReadings.asSharedFlow()

        override suspend fun read(): CabinetResult<TemperatureReading> {
            return submit(FacadeCommand.ReadTemperature())
        }

        override suspend fun setTarget(celsius: Double): CabinetResult<Unit> {
            return submit(FacadeCommand.SetTargetTemperature(celsius))
        }
    }
}
