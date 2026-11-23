package com.example.environmentmonitor.data

import com.example.environmentmonitor.model.Limits
import com.example.environmentmonitor.model.StationInfo
import kotlinx.coroutines.flow.Flow

interface EnvironmentRepository {
    suspend fun loadStation(): StationInfo
    suspend fun loadLimits(): Limits
    fun temperatureRaw(): Flow<Int>
    fun humidity(): Flow<Int>
    fun failNextTemperatureEmission()
}