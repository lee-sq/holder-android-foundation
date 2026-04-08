package com.holderzone.hardware.scale.driver.ly

import android.content.Context
import android_serialport_api.SerialPort
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class LyScaleDriverFactory : ScaleDriverFactory {
    override val vendor: ScaleVendor = ScaleVendor.LY
    override val defaultPortCandidates: List<ScalePortConfig> =
        ScalePortCatalog.defaultCandidatesFor(vendor)

    override suspend fun open(
        appContext: Context,
        config: ScaleConfig,
        port: ScalePortConfig,
        probeOnly: Boolean,
    ): ScaleResult<ScaleDriver> {
        return LyScaleDriver.create(appContext, config, port, probeOnly)
    }
}

/**
 * 亮悦串口称重 driver。
 *
 * 相比旧实现，这里彻底去掉了静态全局串口工具类，改成每个 facade 会话拥有自己的
 * driver、自己的串口对象、自己的读线程和自己的解析缓冲区。
 */
internal class LyScaleDriver private constructor(
    private val appContext: Context,
    private val config: ScaleConfig,
    private val port: ScalePortConfig,
    private val probeOnly: Boolean,
) : ScaleDriver {

    override val vendor: ScaleVendor = ScaleVendor.LY

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val parser = LyWeightFrameParser()

    private val mutableEvents = MutableSharedFlow<ScaleEvent>(extraBufferCapacity = 32)
    private val mutableReadings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 16)
    private val mutableLastSignalAtMs = MutableStateFlow(0L)

    override val events: Flow<ScaleEvent> = mutableEvents
    override val readings: Flow<WeightReading> = mutableReadings
    override val lastSignalAtMs = mutableLastSignalAtMs.asStateFlow()

    private var serialPort: SerialPort? = null
    private var readJob: Job? = null
    private var latestReading: WeightReading? = null

    companion object {
        private const val TAG = "LyScaleDriver"

        suspend fun create(
            appContext: Context,
            config: ScaleConfig,
            port: ScalePortConfig,
            probeOnly: Boolean,
        ): ScaleResult<ScaleDriver> {
            val driver = LyScaleDriver(appContext, config, port, probeOnly)
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
                    message = "No LY weight reading is available yet.",
                    vendor = vendor,
                )
            )
        }
    }

    override suspend fun tare(): ScaleResult<Unit> {
        return sendCommand(byteArrayOf(0x54))
    }

    override suspend fun zero(): ScaleResult<Unit> {
        return sendCommand(byteArrayOf(0x5A))
    }

    private suspend fun start(): ScaleResult<ScaleDriver> {
        return runCatching {
            serialPort = SerialPort(File(port.device), port.baudRate, 0)
            running.set(true)
            mutableEvents.emit(ScaleEvent.Connected(vendor))
            readJob = scope.launch {
                readLoop()
            }
            logger.info(TAG, "LY scale driver started on ${port.device}@${port.baudRate}")
            ScaleResult.Ok(this as ScaleDriver)
        }.getOrElse { throwable ->
            shutdown()
            ScaleResult.Err(
                ScaleError.DeviceUnavailable(
                    message = throwable.message ?: "Failed to initialize LY scale driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private suspend fun readLoop() {
        val buffer = ByteArray(512)
        while (running.get()) {
            val port = serialPort ?: break
            val size = try {
                port.inputStream.read(buffer)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: IOException) {
                handleReadFailure(exception)
                break
            } catch (throwable: Throwable) {
                handleReadFailure(throwable)
                break
            }

            if (size <= 0) {
                continue
            }

            parser.append(buffer, size).forEach { frame ->
                val reading = frame.toWeightReading()
                latestReading = reading
                mutableLastSignalAtMs.value = System.currentTimeMillis()
                mutableReadings.tryEmit(reading)
                mutableEvents.tryEmit(ScaleEvent.WeightUpdated(reading))
            }
        }
    }

    private suspend fun sendCommand(command: ByteArray): ScaleResult<Unit> {
        val port = serialPort
            ?: return ScaleResult.Err(
                ScaleError.DeviceUnavailable(
                    message = "LY serial port is unavailable.",
                    vendor = vendor,
                )
            )
        return withContext(Dispatchers.IO) {
            runCatching {
                writeMutex.withLock {
                    port.outputStream.write(command)
                    port.outputStream.flush()
                }
                ScaleResult.Ok(Unit)
            }.getOrElse { throwable ->
                ScaleResult.Err(
                    ScaleError.OperationFailed(
                        message = throwable.message ?: "Failed to send LY scale command.",
                        vendor = vendor,
                        cause = throwable,
                    )
                )
            }
        }
    }

    private fun handleReadFailure(throwable: Throwable) {
        if (!running.get()) {
            return
        }
        logger.error(TAG, "LY scale read loop failed.", throwable)
        mutableEvents.tryEmit(
            ScaleEvent.Error(
                ScaleError.Communication(
                    message = throwable.message ?: "LY scale read loop failed.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        )
    }

    private fun shutdown(): ScaleResult<Unit> {
        return runCatching {
            running.set(false)
            readJob?.cancel()
            readJob = null
            scope.coroutineContext.cancelChildren()
            serialPort?.close()
            serialPort = null
            parser.reset()
            latestReading = null
            mutableLastSignalAtMs.value = 0L
            mutableEvents.tryEmit(ScaleEvent.Disconnected(vendor))
            logger.info(TAG, "LY scale driver stopped")
            ScaleResult.Ok(Unit)
        }.getOrElse { throwable ->
            ScaleResult.Err(
                ScaleError.OperationFailed(
                    message = throwable.message ?: "Failed to stop LY scale driver.",
                    vendor = vendor,
                    cause = throwable,
                )
            )
        }
    }

    private data class LyParsedFrame(
        val netWeightKg: Double,
        val tareWeightKg: Double,
        val stable: Boolean,
        val netMode: Boolean,
        val zero: Boolean,
        val rawFrame: String,
    ) {
        fun toWeightReading(): WeightReading {
            return WeightReading(
                grams = kilogramsToGrams(netWeightKg),
                tareGrams = kilogramsToGrams(tareWeightKg),
                stable = stable,
                netMode = netMode,
                zero = zero,
                rawFrame = rawFrame,
            )
        }
    }

    /**
     * 亮悦串口帧解析器。
     *
     * 解析器只负责把原始串口数据拼成 26 字节标准帧，并转换为结构化数据。
     * 它不持有串口对象，也不控制线程生命周期。
     */
    private class LyWeightFrameParser {
        private val dataPool = ByteArray(MAX_SIZE)
        private var currentSize = 0

        fun append(source: ByteArray, size: Int): List<LyParsedFrame> {
            if (size <= 0) {
                return emptyList()
            }

            if (currentSize + size > MAX_SIZE) {
                currentSize = 0
            }

            source.copyInto(dataPool, destinationOffset = currentSize, startIndex = 0, endIndex = size)
            currentSize += size

            val frames = mutableListOf<LyParsedFrame>()
            var scanIndex = 0
            var consumedUntil = 0
            while (scanIndex < currentSize) {
                if (dataPool[scanIndex] == START_FLAG && currentSize - scanIndex >= FRAME_LENGTH) {
                    val frameBytes = dataPool.copyOfRange(scanIndex, scanIndex + FRAME_LENGTH)
                    parseFrame(frameBytes)?.let { frame ->
                        frames += frame
                        consumedUntil = scanIndex + FRAME_LENGTH
                        scanIndex = consumedUntil
                        continue
                    }
                }
                scanIndex += 1
            }

            if (consumedUntil > 0) {
                val remaining = currentSize - consumedUntil
                if (remaining > 0) {
                    dataPool.copyInto(dataPool, destinationOffset = 0, startIndex = consumedUntil, endIndex = currentSize)
                }
                currentSize = remaining.coerceAtLeast(0)
            }

            return frames
        }

        fun reset() {
            currentSize = 0
        }

        private fun parseFrame(frameBytes: ByteArray): LyParsedFrame? {
            val rawFrame = String(frameBytes, StandardCharsets.UTF_8)
            return runCatching {
                val netWeightKg = rawFrame.substring(17, 24).trim().toDouble()
                val tareWeightKg = rawFrame.substring(9, 16).trim().toDouble()
                val flag = rawFrame.substring(25, 26)
                LyParsedFrame(
                    netWeightKg = netWeightKg,
                    tareWeightKg = tareWeightKg,
                    stable = flag in STABLE_FLAGS,
                    netMode = tareWeightKg > 0,
                    zero = netWeightKg == 0.0,
                    rawFrame = rawFrame,
                )
            }.getOrNull()
        }

        private companion object {
            const val MAX_SIZE = 2_048
            const val FRAME_LENGTH = 26
            const val START_FLAG: Byte = '='.code.toByte()
            val STABLE_FLAGS = setOf("2", "3", "6", "7")
        }
    }
}
