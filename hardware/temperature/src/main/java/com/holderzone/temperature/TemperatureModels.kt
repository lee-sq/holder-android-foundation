package com.holderzone.temperature

data class TemperatureReading(
    val temperatureDeciC: Int,
    val adValue: Int,
    val timestampMs: Long
) {
    val celsius: Double
        get() = temperatureDeciC / 10.0
}

data class CalibrationPoint(
    val temperatureDeciC: Int,
    val adValue: Int
) {
    val celsius: Double
        get() = temperatureDeciC / 10.0
}

data class CalibrationData(
    val points: List<CalibrationPoint>,
    val timestampWords: List<Int> = List(4) { 0 }
) {
    val pointCount: Int
        get() = points.size
}
