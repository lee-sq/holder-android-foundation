package com.holderzone.temperature

data class TemperatureConfig(
    val port: String = "/dev/ttyS2",
    val baudRate: Int = 115200,
    val deviceAddress: Int = 0x01,
    val requestTimeoutMs: Long = 1_000L,
    val frameIntervalMs: Long = 50L
)
