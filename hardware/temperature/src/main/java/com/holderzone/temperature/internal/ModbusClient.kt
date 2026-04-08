package com.holderzone.temperature.internal

import android.os.SystemClock
import com.bjw.bean.ComBean
import com.bjw.utils.SerialHelper
import com.holderzone.temperature.TemperatureConfig
import com.holderzone.temperature.TemperatureException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class ModbusClient(private val config: TemperatureConfig) {
    private val parser = ModbusRtuParser()
    private val serial = TemperatureSerialHelper(::onBytes)
    private val mutex = Mutex()

    @Volatile
    private var pending: CompletableDeferred<ModbusResponse>? = null

    @Volatile
    private var pendingFunction: Int = 0

    @Volatile
    private var pendingAddress: Int = 0

    @Volatile
    private var lastSendAtMs: Long = 0L

    val isOpen: Boolean
        get() = serial.isOpen

    fun open(): Result<Unit> = runCatching {
        serial.port = config.port
        serial.baudRate = config.baudRate
        if (!serial.isOpen) {
            serial.open()
        }
    }

    fun close() {
        pending = null
        serial.close()
    }

    suspend fun readHoldingRegisters(startAddress: Int, registerCount: Int): ByteArray {
        val request = ModbusRtu.buildReadRequest(
            config.deviceAddress,
            startAddress,
            registerCount
        )
        val response = request(ModbusRtu.READ_HOLDING_REGISTERS, request)
        response.throwOnError()
        return response.payload
    }

    suspend fun writeMultipleRegisters(startAddress: Int, registerValues: IntArray) {
        val request = ModbusRtu.buildWriteRequest(
            config.deviceAddress,
            startAddress,
            registerValues
        )
        val response = request(ModbusRtu.WRITE_MULTIPLE_REGISTERS, request)
        response.throwOnError()
    }

    private suspend fun request(expectedFunction: Int, frame: ByteArray): ModbusResponse {
        return mutex.withLock {
            if (!serial.isOpen) {
                throw TemperatureException("Serial port is not open")
            }
            ensureFrameInterval()
            val deferred = CompletableDeferred<ModbusResponse>()
            pending = deferred
            pendingFunction = expectedFunction
            pendingAddress = config.deviceAddress
            serial.send(frame)
            lastSendAtMs = SystemClock.elapsedRealtime()
            try {
                withTimeout(config.requestTimeoutMs) {
                    deferred.await()
                }
            } catch (e: TimeoutCancellationException) {
                throw TemperatureException("Modbus response timeout", cause = e)
            } finally {
                pending = null
            }
        }
    }

    private suspend fun ensureFrameInterval() {
        val elapsed = SystemClock.elapsedRealtime() - lastSendAtMs
        val waitMs = config.frameIntervalMs - elapsed
        if (waitMs > 0) {
            delay(waitMs)
        }
    }

    private fun onBytes(bytes: ByteArray) {
        parser.append(bytes)
        while (true) {
            val response = parser.nextResponse() ?: break
            handleResponse(response)
        }
    }

    private fun handleResponse(response: ModbusResponse) {
        val deferred = pending ?: return
        if (deferred.isCompleted) return
        if (response.address != pendingAddress) return
        if (response.functionCode != pendingFunction) return
        deferred.complete(response)
    }
}

private class TemperatureSerialHelper(
    private val onBytes: (ByteArray) -> Unit
) : SerialHelper() {
    override fun onDataReceived(p0: ComBean?) {
        if (p0 == null) return
        onBytes(p0.bRec.copyOf())
    }
}
