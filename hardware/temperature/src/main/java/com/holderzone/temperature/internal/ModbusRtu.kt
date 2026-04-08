package com.holderzone.temperature.internal

import com.holderzone.temperature.TemperatureException

internal object ModbusRtu {
    const val READ_HOLDING_REGISTERS = 0x03
    const val WRITE_MULTIPLE_REGISTERS = 0x10

    fun buildReadRequest(address: Int, startAddress: Int, registerCount: Int): ByteArray {
        val frame = ByteArray(8)
        frame[0] = address.toByte()
        frame[1] = READ_HOLDING_REGISTERS.toByte()
        frame[2] = (startAddress shr 8).toByte()
        frame[3] = (startAddress and 0xFF).toByte()
        frame[4] = (registerCount shr 8).toByte()
        frame[5] = (registerCount and 0xFF).toByte()
        appendCrc(frame, 6)
        return frame
    }

    fun buildWriteRequest(address: Int, startAddress: Int, registerValues: IntArray): ByteArray {
        val registerCount = registerValues.size
        val byteCount = registerCount * 2
        val frame = ByteArray(9 + byteCount)
        frame[0] = address.toByte()
        frame[1] = WRITE_MULTIPLE_REGISTERS.toByte()
        frame[2] = (startAddress shr 8).toByte()
        frame[3] = (startAddress and 0xFF).toByte()
        frame[4] = (registerCount shr 8).toByte()
        frame[5] = (registerCount and 0xFF).toByte()
        frame[6] = byteCount.toByte()
        registerValues.forEachIndexed { index, value ->
            val normalized = value and 0xFFFF
            val offset = 7 + index * 2
            frame[offset] = (normalized shr 8).toByte()
            frame[offset + 1] = (normalized and 0xFF).toByte()
        }
        appendCrc(frame, frame.size - 2)
        return frame
    }

    fun toUInt16(high: Byte, low: Byte): Int {
        return ((high.toInt() and 0xFF) shl 8) or (low.toInt() and 0xFF)
    }

    fun toInt16(high: Byte, low: Byte): Int {
        return toUInt16(high, low).toShort().toInt()
    }

    fun verifyCrc(frame: ByteArray): Boolean {
        if (frame.size < 3) return false
        val crc = crc16Modbus(frame, frame.size - 2)
        val expected = toUInt16(frame[frame.size - 1], frame[frame.size - 2])
        return crc == expected
    }

    private fun appendCrc(frame: ByteArray, dataLength: Int) {
        val crc = crc16Modbus(frame, dataLength)
        frame[dataLength] = (crc and 0xFF).toByte()
        frame[dataLength + 1] = (crc shr 8).toByte()
    }

    private fun crc16Modbus(data: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                val lsb = crc and 0x0001
                crc = crc ushr 1
                if (lsb == 1) {
                    crc = crc xor 0xA001
                }
            }
        }
        return crc and 0xFFFF
    }
}

internal data class ModbusResponse(
    val address: Int,
    val function: Int,
    val payload: ByteArray,
    val exceptionCode: Int? = null
) {
    val functionCode: Int
        get() = function and 0x7F

    val isException: Boolean
        get() = (function and 0x80) != 0

    fun throwOnError() {
        if (!isException) return
        val message = when (exceptionCode) {
            0x01 -> "Illegal function code"
            0x02 -> "Illegal data address"
            0x03 -> "Illegal data value"
            0x07 -> "CRC error"
            else -> "Modbus error"
        }
        throw TemperatureException(message, exceptionCode)
    }
}

internal class ModbusRtuParser {
    private val buffer = ArrayList<Byte>(256)

    fun append(bytes: ByteArray) {
        for (b in bytes) {
            buffer.add(b)
        }
        if (buffer.size > 2048) {
            buffer.clear()
        }
    }

    fun nextResponse(): ModbusResponse? {
        while (buffer.size >= 5) {
            val address = buffer[0].toInt() and 0xFF
            val function = buffer[1].toInt() and 0xFF
            val expectedLength = when {
                (function and 0x80) != 0 -> 5
                function == ModbusRtu.READ_HOLDING_REGISTERS -> {
                    if (buffer.size < 3) return null
                    val byteCount = buffer[2].toInt() and 0xFF
                    3 + byteCount + 2
                }
                function == ModbusRtu.WRITE_MULTIPLE_REGISTERS -> 8
                else -> {
                    buffer.removeAt(0)
                    continue
                }
            }
            if (buffer.size < expectedLength) return null
            val frame = ByteArray(expectedLength)
            for (i in 0 until expectedLength) {
                frame[i] = buffer[i]
            }
            if (!ModbusRtu.verifyCrc(frame)) {
                buffer.removeAt(0)
                continue
            }
            buffer.subList(0, expectedLength).clear()
            return parseFrame(address, function, frame)
        }
        return null
    }

    private fun parseFrame(address: Int, function: Int, frame: ByteArray): ModbusResponse {
        if ((function and 0x80) != 0) {
            val code = frame[2].toInt() and 0xFF
            return ModbusResponse(address, function, ByteArray(0), code)
        }
        return when (function) {
            ModbusRtu.READ_HOLDING_REGISTERS -> {
                val byteCount = frame[2].toInt() and 0xFF
                val payload = frame.copyOfRange(3, 3 + byteCount)
                ModbusResponse(address, function, payload)
            }
            ModbusRtu.WRITE_MULTIPLE_REGISTERS -> {
                val payload = frame.copyOfRange(2, 6)
                ModbusResponse(address, function, payload)
            }
            else -> ModbusResponse(address, function, ByteArray(0))
        }
    }
}
