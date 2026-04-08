package com.holderzone.temperature

class TemperatureException(
    message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
