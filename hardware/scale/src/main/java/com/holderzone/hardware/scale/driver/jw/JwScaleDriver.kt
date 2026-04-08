package com.holderzone.hardware.scale.driver.jw

import android.content.Context
import android.os.Build
import com.holderzone.hardware.scale.ScaleCapabilities
import com.holderzone.hardware.scale.ScaleConfig
import com.holderzone.hardware.scale.ScaleError
import com.holderzone.hardware.scale.ScaleEvent
import com.holderzone.hardware.scale.ScaleLogger
import com.holderzone.hardware.scale.ScalePortConfig
import com.holderzone.hardware.scale.ScaleResult
import com.holderzone.hardware.scale.ScaleVendor
import com.holderzone.hardware.scale.WeightReading
import com.holderzone.hardware.scale.kilogramsToGrams
import com.holderzone.hardware.scale.internal.ScalePortCatalog
import com.holderzone.hardware.scale.internal.spi.ScaleDriver
import com.holderzone.hardware.scale.internal.spi.ScaleDriverFactory
import com.tongdeliu.jwbaselib.WeighingDeviceManager
import com.tongdeliu.jwbaselib.callback.WeightDeviceCallBack
import com.tongdeliu.jwbaselib.json.DeviceWeightInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

class JwScaleDriverFactory : ScaleDriverFactory {
    override val vendor: ScaleVendor = ScaleVendor.JW
    override val defaultPortCandidates: List<ScalePortConfig> =
        ScalePortCatalog.defaultCandidatesFor(vendor)

    override suspend fun open(
        appContext: Context,
        config: ScaleConfig,
        port: ScalePortConfig,
        probeOnly: Boolean,
    ): ScaleResult<ScaleDriver> {
        return JwScaleDriver.create(appContext, config, port, probeOnly)
    }
}

/**
 * JW 厂商称重 driver。
 *
 * 这一层只负责把厂商 SDK 回调转换为统一的重量流与错误模型，
 * 并对外保证资源释放路径是清晰和幂等的。
 */
internal class JwScaleDriver private constructor(
    private val appContext: Context,
    private val config: ScaleConfig,
    private val port: ScalePortConfig,
    private val probeOnly: Boolean,
) : ScaleDriver {

    override val vendor: ScaleVendor = ScaleVendor.JW

    override val capabilities: ScaleCapabilities = ScaleCapabilities(
        selectedVendor = vendor,
        readWeight = true,
        streamWeight = true,
        tare = true,
        zero = true,
        stableSignal = true,
    )

    private val logger: ScaleLogger = config.logger
    private val running = AtomicBoolean(false)
    private val mutableEvents = MutableSharedFlow<ScaleEvent>(extraBufferCapacity = 32)
    private val mutableReadings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 16)
    private val mutableLastSignalAtMs = MutableStateFlow(0L)

    override val events: Flow<ScaleEvent> = mutableEvents
    override val readings: Flow<WeightReading> = mutableReadings
    override val lastSignalAtMs = mutableLastSignalAtMs.asStateFlow()

    private var latestReading: WeightReading? = null
    private var deviceManager: WeighingDeviceManager? = null

    companion object {
        private const val TAG = "JwScaleDriver"

        suspend fun create(
            appContext: Context,
            config: ScaleConfig,
            port: ScalePortConfig,
            probeOnly: Boolean,
        ): ScaleResult<ScaleDriver> {
            val driver = JwScaleDriver(appContext, config, port, probeOnly)
            return driver.start()
        }
    }

    override fun isRunning(): Boolean = running.get()

    override suspend fun stop(): ScaleResult<Unit> {
        return shutdown()
    }

    override fun close() {
        runBlocking {
            shutdown()
        }
    }

    override suspend fun readOnce(): ScaleResult<WeightReading> {
        val reading = latestReading
        return if (reading != null) {
            ScaleResult.Ok(reading)
        } else {
            ScaleResult.Err(
                ScaleError.DeviceUnavailable(
                    message = "No JW weight reading is available yet.",
                    vendor = vendor,
                )
            )
        }
    }

    override suspend fun tare(): ScaleResult<Unit> {
        val manager = deviceManager
            ?: return ScaleResult.Err(
                ScaleError.DeviceUnavailable(
                    message = "JW device manager is unavailable.",
                    vendor = vendor,
                )
            )
        return runCatching {
            manager.resetPeel()
            ScaleResult.Ok(Unit)
        }.getOrElse { throwable ->
            ScaleResult.Err(
                ScaleError.OperationFailed(
                    message = throwable.message ?: "Failed to execute JW tare.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun zero(): ScaleResult<Unit> {
        val manager = deviceManager
            ?: return ScaleResult.Err(
                ScaleError.DeviceUnavailable(
                    message = "JW device manager is unavailable.",
                    vendor = vendor,
                )
            )
        return runCatching {
            manager.resetZero()
            ScaleResult.Ok(Unit)
        }.getOrElse { throwable ->
            ScaleResult.Err(
                ScaleError.OperationFailed(
                    message = throwable.message ?: "Failed to execute JW zero.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private suspend fun start(): ScaleResult<ScaleDriver> {
        return runCatching {
            val isAndroid10 = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
            val manager = WeighingDeviceManager.getInstance(
                appContext,
                isAndroid10,
                config.vendorHints.jwUseSecondaryScreen,
            )
            deviceManager = manager
            manager.setWeightDeviceCallBack(JwWeightCallback())
            manager.init(7, port.device, port.baudRate)
            running.set(true)
            mutableEvents.emit(ScaleEvent.Connected(vendor))
            logger.info(TAG, "JW scale driver started on ${port.device}@${port.baudRate}")
            ScaleResult.Ok(this as ScaleDriver)
        }.getOrElse { throwable ->
            shutdown()
            ScaleResult.Err(
                ScaleError.DeviceUnavailable(
                    message = throwable.message ?: "Failed to initialize JW scale driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private fun shutdown(): ScaleResult<Unit> {
        return runCatching {
            val manager = deviceManager
            deviceManager = null
            manager?.setWeightDeviceCallBack(null)
            manager?.realWeightManager()
            running.set(false)
            mutableLastSignalAtMs.value = 0L
            mutableEvents.tryEmit(ScaleEvent.Disconnected(vendor))
            logger.info(TAG, "JW scale driver stopped")
            ScaleResult.Ok(Unit)
        }.getOrElse { throwable ->
            ScaleResult.Err(
                ScaleError.OperationFailed(
                    message = throwable.message ?: "Failed to stop JW scale driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private fun publishReading(reading: WeightReading) {
        latestReading = reading
        mutableLastSignalAtMs.value = System.currentTimeMillis()
        mutableReadings.tryEmit(reading)
        mutableEvents.tryEmit(ScaleEvent.WeightUpdated(reading))
    }

    private inner class JwWeightCallback : WeightDeviceCallBack {
        override fun onRealWeightInformationCallBack(deviceWeightInfo: DeviceWeightInfo) {
            val reading = WeightReading(
                grams = kilogramsToGrams(deviceWeightInfo.real_weight.toDouble()),
                tareGrams = kilogramsToGrams(deviceWeightInfo.peel_value.toDouble()),
                stable = deviceWeightInfo.holod_state == 1,
                netMode = deviceWeightInfo.peel_value.toDouble() > 0,
                zero = deviceWeightInfo.zero_weight > 0,
            )
            publishReading(reading)
        }

        override fun onCalibrationInformationCallBack(
            p0: String?,
            p1: Int,
            p2: Int,
            p3: Array<out IntArray?>?,
            p4: String?,
        ) = Unit

        override fun onControlSuccessCallBack() {
            logger.debug(TAG, "JW control command completed.")
        }

        override fun onVersionSuccessCallBack(version: String?) {
            logger.debug(TAG, "JW version reported: ${version.orEmpty()}")
        }

        override fun onWriteCalibrationInformationCallBack(
            p0: String?,
            p1: String?,
            p2: Double,
        ) = Unit

        override fun onSecondaryScreenInformationCallBack(p0: Int, p1: Int) = Unit

        override fun onSetBatteryCoefficientCallBack() = Unit

        override fun onSetSecondaryScreenBrightnessCallBack() = Unit
    }

}
