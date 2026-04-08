package com.holderzone.hardware.cabinet.equitment.model

/**
 * 串口配置。
 *
 * @param device 设备路径（如 /dev/ttyS4）
 * @param baudRate 波特率
 * @param dataBits 数据位，默认 8
 * @param stopBits 停止位，默认 1
 * @param parity 校验位，默认 0（无校验）
 */

data class SerialPortConfig(
    val device: String,
    val baudRate: Int,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0,
)