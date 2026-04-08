package com.holderzone.hardware.cabinet.print

import android.util.Base64
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

/**
 * TSPL 标签命令构建器。
 *
 * 负责把标签元素逐条拼装成最终的打印字节流。
 */
internal class LabelCommand : PrinterCommand {
    private val command: MutableList<Byte> = mutableListOf()

    /**
     * 设置标签尺寸，单位是毫米。
     */
    fun addSize(width: Int, height: Int) {
        addCommand("SIZE $width mm,$height mm\r\n")
    }

    /**
     * 设置标签间隙。
     */
    fun addGap(gap: Int) {
        addCommand("GAP $gap mm,0 mm\r\n")
    }

    /**
     * 设置打印方向和镜像模式。
     */
    fun addDirection(direction: Direction, mirror: Mirror) {
        addCommand("DIRECTION ${direction.value},${mirror.value}\r\n")
    }

    /**
     * 设置参考原点。
     */
    fun addReference(x: Int, y: Int) {
        addCommand("REFERENCE $x,$y\r\n")
    }

    /**
     * 设置是否启用撕纸模式。
     */
    fun addTear(enable: Enable) {
        addCommand("SET TEAR ${enable.value}\r\n")
    }

    /**
     * 清空当前页内容。
     */
    fun addCls() {
        addCommand("CLS\r\n")
    }

    /**
     * 执行打印。
     */
    fun addPrint(m: Int, n: Int) {
        addCommand("PRINT $m,$n\r\n")
    }

    /**
     * 添加文本。
     */
    fun addText(
        x: Int,
        y: Int,
        font: FontType,
        rotation: Rotation,
        xScale: FontMultiplier,
        yScale: FontMultiplier,
        text: String,
    ) {
        addCommand(
            "TEXT $x,$y,\"${font.value}\",${rotation.value},${xScale.value},${yScale.value},\"$text\"\r\n",
            font.charset,
        )
    }

    /**
     * 添加二维码。
     */
    fun addQRCode(
        x: Int,
        y: Int,
        level: ErrorCorrectionLevel,
        cellWidth: Int,
        rotation: Rotation,
        data: String,
    ) {
        addCommand("QRCODE $x,$y,${level.value},$cellWidth,A,M2,S7,${rotation.value},\"$data\"\r\n")
    }

    private fun addCommand(command: String, charset: Charset = charset("GBK")) {
        try {
            this.command.addAll(command.toByteArray(charset).asList())
        } catch (_: UnsupportedEncodingException) {
        }
    }

    override fun getCommandBytes(): ByteArray {
        return command.toByteArray()
    }

    override fun toString(): String {
        return Base64.encodeToString(getCommandBytes(), Base64.NO_WRAP)
    }
}

/**
 * 打印命令抽象。
 */
internal interface PrinterCommand {
    fun getCommandBytes(): ByteArray
}

/**
 * 标签打印方向。
 */
internal enum class Direction(val value: Int) {
    FORWARD(0),
    BACKWARD(1),
}

/**
 * 标签镜像模式。
 */
internal enum class Mirror(val value: Int) {
    NORMAL(0),
    MIRROR(1),
}

/**
 * 开关型参数。
 */
internal enum class Enable(val value: Int) {
    OFF(0),
    ON(1),
}

/**
 * 二维码纠错级别。
 */
internal enum class ErrorCorrectionLevel(val value: String) {
    LEVEL_L("L"),
    LEVEL_M("M"),
    LEVEL_Q("Q"),
    LEVEL_H("H"),
}

/**
 * 打印旋转角度。
 */
internal enum class Rotation(val value: Int) {
    ROTATION_0(0),
    ROTATION_90(90),
    ROTATION_180(180),
    ROTATION_270(270),
}

/**
 * TSPL 字体定义。
 */
internal enum class FontType(
    val value: String,
    val charset: Charset = charset("gb2312"),
) {
    SIMPLIFIED_CHINESE("TSS24.BF2", Charset.forName("GB18030")),
    TRADITIONAL_CHINESE("TST24.BF2", Charset.forName("Big5")),
    KOREAN("K", Charset.forName("CP949")),
}

/**
 * 字体缩放倍数。
 */
internal enum class FontMultiplier(val value: Int) {
    MUL_1(1),
    MUL_2(2),
    MUL_3(3),
    MUL_4(4),
}
