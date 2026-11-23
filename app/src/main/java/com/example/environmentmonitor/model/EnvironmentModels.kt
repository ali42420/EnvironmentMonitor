package com.example.environmentmonitor.model

data class StationInfo(
    val id: String,
    val name: String
)

data class Limits(
    val maxTempC: Double,
    val maxHumidity: Int
)

data class StartupData(
    val station: StationInfo,
    val limits: Limits
)

data class Reading(
    val id: Long,
    val tempC: Double,
    val humidity: Int,
    val isAlert: Boolean
)