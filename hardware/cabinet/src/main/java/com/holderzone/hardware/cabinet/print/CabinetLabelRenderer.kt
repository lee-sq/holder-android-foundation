package com.holderzone.hardware.cabinet.print

import com.holderzone.hardware.cabinet.PrintRequest
import com.kongqw.serialportlibrary.command.Label
import java.util.Vector

/**
 * Cabinet SDK 统一标签渲染入口。
 *
 * 不同厂商的打印协议存在差异，但业务层只传 [PrintRequest.Label]，
 * 真正的协议细节在这里按厂商进行分流渲染。
 */
object CabinetLabelRenderer {

    /**
     * 渲染通用 TSPL 标签。
     */
    fun renderCommonLabel(request: PrintRequest.Label): ByteArray {
        val x = 10
        var y = 20
        val label = LabelCommand().apply {
            addSize(request.widthMm, request.heightMm)
            addGap(3)
            addDirection(Direction.FORWARD, Mirror.NORMAL)
            addReference(0, 0)
            addTear(Enable.ON)
            addCls()
        }

        request.title?.takeIf(String::isNotBlank)?.let { title ->
            label.addText(
                x = 48,
                y = y,
                font = FontType.SIMPLIFIED_CHINESE,
                rotation = Rotation.ROTATION_0,
                xScale = FontMultiplier.MUL_2,
                yScale = FontMultiplier.MUL_1,
                text = title,
            )
            y += 38
        }

        request.qrCode?.takeIf(String::isNotBlank)?.let { qrCode ->
            // 二维码固定放在右上区域，避免和正文文本发生重叠。
            label.addQRCode(
                x = x + 220,
                y = y - 3,
                level = ErrorCorrectionLevel.LEVEL_L,
                cellWidth = 3,
                rotation = Rotation.ROTATION_0,
                data = qrCode,
            )
        }

        request.lines.forEach { line ->
            label.addText(
                x = x,
                y = y,
                font = FontType.SIMPLIFIED_CHINESE,
                rotation = Rotation.ROTATION_0,
                xScale = FontMultiplier.MUL_1,
                yScale = FontMultiplier.MUL_1,
                text = line,
            )
            y += 38
        }

        label.addPrint(1, 1)
        return label.getCommandBytes()
    }

    /**
     * 渲染 Star 打印机标签。
     */
    fun renderStarLabel(request: PrintRequest.Label): ByteArray {
        val x = 10
        var y = 20
        val width = request.widthMm * 8
        val height = request.heightMm * 8
        val label = Label().apply {
            reset()
            clear()
            switchLabel()
            customPageStart(width, height, 0)
        }

        request.title?.takeIf(String::isNotBlank)?.let { title ->
            // Star 打印协议没有通用居中能力，这里手动估算标题起始位置。
            label.customPrintText(
                title,
                ((width - 24 * 1.5 * title.length) / 2).toInt(),
                y,
                24,
                1,
                0,
                0,
                0,
                9,
                0,
                0,
            )
            y += 38
        }

        request.lines.forEach { line ->
            label.customPrintText(line, x, y, 24, 0, 0, 0, 0, 9, 0, 0)
            y += 38
        }

        request.qrCode?.takeIf(String::isNotBlank)?.let { qrCode ->
            label.printQRcode(qrCode, 0, 0, x + 220, 58, 3, 0)
        }

        label.pageEnd()
        label.customPrintPage(1)
        return toByteArray(label.command)
    }

    /**
     * 把厂商 SDK 的 `Vector<Byte>` 转成普通字节数组，便于统一下发。
     */
    private fun toByteArray(data: Vector<Byte>): ByteArray {
        val bytes = ByteArray(data.size)
        data.forEachIndexed { index, value ->
            bytes[index] = value
        }
        return bytes
    }
}
