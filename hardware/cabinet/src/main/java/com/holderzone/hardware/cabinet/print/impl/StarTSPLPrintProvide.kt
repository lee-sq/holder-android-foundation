package com.holderzone.hardware.cabinet.print.impl

import com.holderzone.hardware.cabinet.print.CommonTSPLPrintProvide
import com.holderzone.hardware.cabinet.print.PrinterState

class StarTSPLPrintProvide : CommonTSPLPrintProvide() {
    override fun analysisByteArray(byteArray: ByteArray): PrinterState {
        if (byteArray.size >= 3 && (byteArray[0].toInt() and 0xFF) == 0xFE) {
            // 0xFE 前缀是 Star 打印机状态查询结果。
            val code = byteArray[1].toInt() and 0xFF
            val value = byteArray[2].toInt() and 0xFF
            return when (code) {
                0x23 -> when (value) {
                    0x1A -> PrinterState.PAPER_OUT
                    0x12 -> PrinterState.READY
                    else -> PrinterState.UNKNOWN
                }

                0x24 -> when (value) {
                    0x10 -> PrinterState.PAPER_OUT
                    0x11 -> PrinterState.READY
                    else -> PrinterState.UNKNOWN
                }

                0x25,
                0x26,
                0x2B,
                -> if (value == 0x11) PrinterState.ERROR else PrinterState.UNKNOWN

                0x27 -> if (value == 0x11) PrinterState.HEAD_OPEN else PrinterState.UNKNOWN
                0x28 -> if (value == 0x11) PrinterState.READY else PrinterState.ERROR
                else -> PrinterState.UNKNOWN
            }
        }
        if (byteArray.size >= 3 && (byteArray[0].toInt() and 0xFF) == 0xFC) {
            // 0xFC 前缀是 Star 的文本应答，常见返回 OK / no。
            val first = byteArray[1].toInt() and 0xFF
            val second = byteArray[2].toInt() and 0xFF
            return when {
                first == 0x4F && second == 0x4B -> PrinterState.READY
                first == 0x6E && second == 0x6F -> PrinterState.BUSY
                else -> PrinterState.UNKNOWN
            }
        }
        return PrinterState.UNKNOWN
    }
}
