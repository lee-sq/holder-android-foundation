package com.holderzone.temperature

import com.holderzone.temperature.internal.ModbusClient
import com.holderzone.temperature.internal.ModbusRtu

class TemperatureMeter(
    private val config: TemperatureConfig = TemperatureConfig()
) {
    private val client = ModbusClient(config)

    fun open(): Result<Unit> = client.open()

    fun close() {
        client.close()
    }

    fun isOpen(): Boolean = client.isOpen

    suspend fun readVersion(): Result<String> = runCatching {
        val payload = client.readHoldingRegisters(0x0200, 1)
        if (payload.isEmpty()) {
            throw TemperatureException("Empty version response")
        }
        String(payload, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
    }

    suspend fun readTemperature(): Result<TemperatureReading> = runCatching {
        val payload = client.readHoldingRegisters(0x0000, 2)
        if (payload.size < 4) {
            throw TemperatureException("Invalid temperature response length")
        }
        val temperature = ModbusRtu.toInt16(payload[0], payload[1])
        val adValue = ModbusRtu.toUInt16(payload[2], payload[3])
        TemperatureReading(
            temperatureDeciC = temperature,
            adValue = adValue,
            timestampMs = System.currentTimeMillis()
        )
    }

    suspend fun controlBuzzer(onTimeMs: Int, offTimeMs: Int, repeatCount: Int): Result<Unit> =
        runCatching {
            if (onTimeMs !in 20..2000) {
                throw TemperatureException("Buzzer on time must be between 20 and 2000 ms")
            }
            if (offTimeMs !in 20..2000) {
                throw TemperatureException("Buzzer off time must be between 20 and 2000 ms")
            }
            if (repeatCount !in 1..10) {
                throw TemperatureException("Buzzer repeat count must be between 1 and 10")
            }
            val registers = intArrayOf(onTimeMs, offTimeMs, repeatCount)
            client.writeMultipleRegisters(0x0002, registers)
        }

    suspend fun readCalibration(): Result<CalibrationData> = runCatching {
        val payload = client.readHoldingRegisters(0x0100, 13)
        if (payload.size < 26) {
            throw TemperatureException("Invalid calibration response length")
        }
        val pointCount = ModbusRtu.toUInt16(payload[0], payload[1])
        if (pointCount > 4) {
            throw TemperatureException("Invalid calibration point count: $pointCount")
        }
        val points = buildList {
            if (pointCount >= 1) add(readPoint(payload, 2))
            if (pointCount >= 2) add(readPoint(payload, 6))
            if (pointCount >= 3) add(readPoint(payload, 10))
            if (pointCount >= 4) add(readPoint(payload, 14))
        }
        val timestamps = listOf(
            ModbusRtu.toUInt16(payload[18], payload[19]),
            ModbusRtu.toUInt16(payload[20], payload[21]),
            ModbusRtu.toUInt16(payload[22], payload[23]),
            ModbusRtu.toUInt16(payload[24], payload[25])
        )
        CalibrationData(points = points, timestampWords = timestamps)
    }

    suspend fun writeCalibration(data: CalibrationData): Result<Unit> = runCatching {
        validateCalibration(data.points)
        val points = data.points
        val timestamps = normalizeTimestamps(data.timestampWords)
        val registers = IntArray(13)
        registers[0] = points.size
        writePoint(registers, 1, points.getOrNull(0))
        writePoint(registers, 3, points.getOrNull(1))
        writePoint(registers, 5, points.getOrNull(2))
        writePoint(registers, 7, points.getOrNull(3))
        registers[9] = timestamps[0]
        registers[10] = timestamps[1]
        registers[11] = timestamps[2]
        registers[12] = timestamps[3]
        client.writeMultipleRegisters(0x0100, registers)
    }

    private fun readPoint(payload: ByteArray, offset: Int): CalibrationPoint {
        val temperature = ModbusRtu.toInt16(payload[offset], payload[offset + 1])
        val adValue = ModbusRtu.toUInt16(payload[offset + 2], payload[offset + 3])
        return CalibrationPoint(temperatureDeciC = temperature, adValue = adValue)
    }

    private fun writePoint(registers: IntArray, index: Int, point: CalibrationPoint?) {
        registers[index] = point?.temperatureDeciC ?: 0
        registers[index + 1] = point?.adValue ?: 0
    }

    private fun validateCalibration(points: List<CalibrationPoint>) {
        if (points.size !in 2..4) {
            throw TemperatureException("Calibration requires 2 to 4 points")
        }
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val next = points[i]
            if (next.adValue < prev.adValue || next.temperatureDeciC < prev.temperatureDeciC) {
                throw TemperatureException("Calibration points must be sorted by temperature and AD value")
            }
        }
    }

    private fun normalizeTimestamps(source: List<Int>): List<Int> {
        if (source.size >= 4) return source.take(4)
        val padded = ArrayList<Int>(4)
        padded.addAll(source)
        while (padded.size < 4) {
            padded.add(0)
        }
        return padded
    }
}
