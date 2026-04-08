package com.holderzone.logger

data class LoggerInitConfig(
    val fileDir: String? = null,
    val serverUrl: String? = null,
    val enableFileStrategy: Boolean = true,
    val enableServerStrategy: Boolean = false,
    val showThreadInfo: Boolean = false,
    val methodCount: Int = 0,
)
