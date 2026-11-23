package com.example.environmentmonitor.ui

import com.example.environmentmonitor.model.Reading
import com.example.environmentmonitor.model.StartupData

enum class StartupStatus {
    LOADING,
    READY,
    ERROR
}

enum class MonitoringStatus {
    IDLE,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}

data class MonitorUiState(
    val startupStatus: StartupStatus = StartupStatus.LOADING,
    val startupData: StartupData? = null,
    val startupDurationMs: Long? = null,
    val startupErrorMessage: String? = null,
    val monitoringStatus: MonitoringStatus = MonitoringStatus.IDLE,
    val monitoringErrorMessage: String? = null,
    val latestReading: Reading? = null,
    val history: List<Reading> = emptyList()
) {
    // آمارهای تاریخچه مستقیماً از همان لیست حداکثر ۳۰ تایی محاسبه می‌شوند
    val readingCount: Int
        get() = history.size

    val averageTempC: Double?
        get() = if (history.isEmpty()) null else history.map { it.tempC }.average()

    val alertCount: Int
        get() = history.count { it.isAlert }

    val isRunning: Boolean
        get() = monitoringStatus == MonitoringStatus.RUNNING

    val isStartStopEnabled: Boolean
        get() = startupStatus == StartupStatus.READY && monitoringStatus != MonitoringStatus.STOPPING
}