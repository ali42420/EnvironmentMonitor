package com.example.environmentmonitor.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.environmentmonitor.data.EnvironmentRepository
import com.example.environmentmonitor.model.Reading
import com.example.environmentmonitor.model.StartupData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitorViewModel(
    private val repository: EnvironmentRepository,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MonitorEvent>(replay = 0)
    val events: SharedFlow<MonitorEvent> = _events.asSharedFlow()

    private val _flowLabState = MutableStateFlow(FlowLabState())
    val flowLabState: StateFlow<FlowLabState> = _flowLabState.asStateFlow()

    private var startupJob: Job? = null
    private var monitoringJob: Job? = null
    private var labJob: Job? = null

    // برای سریال‌سازی گذارهای شروع، توقف و تلاش دوباره
    private val transitionMutex = Mutex()
    private var nextReadingId: Long = 1L

    init {
        loadStartupData()
    }

    // ==================== گام اول: آماده‌سازی هم‌زمان ====================

    fun loadStartupData() {
        if (startupJob?.isActive == true) return

        startupJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    startupStatus = StartupStatus.LOADING,
                    startupErrorMessage = null
                )
            }
            Log.d("EnvJob", "Startup loading started")

            try {
                // هر دو async پیش از اولین await درون coroutineScope ساخته می‌شوند
                val data = coroutineScope {
                    val stationDeferred = async { repository.loadStation() }
                    val limitsDeferred = async { repository.loadLimits() }
                    StartupData(
                        station = stationDeferred.await(),
                        limits = limitsDeferred.await()
                    )
                }
                val duration = System.currentTimeMillis() - startTime
                Log.d("EnvJob", "Startup completed in ${duration}ms: $data")

                _uiState.update {
                    it.copy(
                        startupStatus = StartupStatus.READY,
                        startupData = data,
                        startupDurationMs = duration,
                        startupErrorMessage = null,
                        monitoringStatus = MonitoringStatus.IDLE
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.e("EnvJob", "Startup failed after ${duration}ms: ${e.message}")
                _uiState.update {
                    it.copy(
                        startupStatus = StartupStatus.ERROR,
                        startupDurationMs = duration,
                        startupErrorMessage = e.message ?: "خطا در آماده‌سازی اولیه"
                    )
                }
            }
        }
    }

    // ==================== گام دوم و سوم: پایش، توقف و خطا ====================

    fun startMonitoring() {
        viewModelScope.launch {
            transitionMutex.withLock {
                val startupData = _uiState.value.startupData ?: return@withLock
                if (_uiState.value.startupStatus != StartupStatus.READY) return@withLock
                if (monitoringJob?.isActive == true) {
                    Log.d("EnvJob", "Start ignored: monitoring already active")
                    return@withLock
                }

                startMonitoringInternal(startupData)
            }
        }
    }

    private fun startMonitoringInternal(startupData: StartupData) {
        val limits = startupData.limits
        Log.d("EnvJob", "Starting monitoring job")

        _uiState.update {
            it.copy(
                monitoringStatus = MonitoringStatus.RUNNING,
                monitoringErrorMessage = null
            )
        }
        _events.tryEmit(MonitorEvent.MonitoringStarted)

        lateinit var currentJob: Job
        currentJob = viewModelScope.launch {
            val temperatures: Flow<Double> = repository.temperatureRaw()
                .filter { raw ->
                    val isValid = raw != -999
                    if (!isValid) {
                        Log.w(
                            "EnvFlow",
                            "Rejected invalid temperature sample: $raw on thread=${Thread.currentThread().name}"
                        )
                    }
                    isValid
                }
                .map { raw -> raw / 10.0 }
                .flowOn(workerDispatcher)

            val humidities: Flow<Int> = repository.humidity()
                .flowOn(workerDispatcher)

            combine(temperatures, humidities) { tempC, humidity ->
                val isAlert = tempC >= limits.maxTempC || humidity >= limits.maxHumidity
                Reading(
                    id = nextReadingId++,
                    tempC = tempC,
                    humidity = humidity,
                    isAlert = isAlert
                )
            }
                // onCompletion پیش از catch برای مشاهده علت اصلی پایان
                .onCompletion { cause ->
                    when (cause) {
                        null -> Log.d("EnvFlow", "Combined flow completed normally")
                        is CancellationException -> Log.d("EnvFlow", "Combined flow cancelled")
                        else -> Log.e("EnvFlow", "Combined flow failed with error: ${cause.message}")
                    }
                }
                .catch { cause ->
                    if (cause is CancellationException) throw cause
                    Log.e("EnvJob", "Monitoring error caught: ${cause.message}")
                    if (monitoringJob === currentJob) {
                        _uiState.update {
                            it.copy(
                                monitoringStatus = MonitoringStatus.ERROR,
                                monitoringErrorMessage = cause.message ?: "خطا در دریافت داده حسگر"
                            )
                        }
                    }
                }
                .collect { reading ->
                    Log.d(
                        "EnvFlow",
                        "Collected reading #$reading on thread=${Thread.currentThread().name}"
                    )
                    if (monitoringJob === currentJob) {
                        _uiState.update { state ->
                            val updatedHistory = (listOf(reading) + state.history)
                                .take(MAX_HISTORY_SIZE)
                                .toList()
                            state.copy(
                                latestReading = reading,
                                history = updatedHistory
                            )
                        }
                    }
                }
        }

        monitoringJob = currentJob
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            transitionMutex.withLock {
                val jobToCancel = monitoringJob
                if (jobToCancel == null || !jobToCancel.isActive) return@withLock

                Log.d("EnvJob", "Stopping monitoring job...")
                _uiState.update { it.copy(monitoringStatus = MonitoringStatus.STOPPING) }

                jobToCancel.cancelAndJoin()
                if (monitoringJob === jobToCancel) {
                    monitoringJob = null
                }

                Log.d("EnvJob", "Monitoring job stopped cleanly")
                _uiState.update {
                    it.copy(
                        monitoringStatus = MonitoringStatus.STOPPED,
                        monitoringErrorMessage = null
                    )
                }
                _events.tryEmit(MonitorEvent.MonitoringStopped)
            }
        }
    }

    fun triggerNextTemperatureError() {
        if (_uiState.value.monitoringStatus == MonitoringStatus.RUNNING) {
            repository.failNextTemperatureEmission()
        }
    }

    fun retryMonitoring() {
        viewModelScope.launch {
            transitionMutex.withLock {
                val startupData = _uiState.value.startupData ?: return@withLock
                if (_uiState.value.monitoringStatus != MonitoringStatus.ERROR) return@withLock

                monitoringJob?.cancelAndJoin()
                monitoringJob = null
                startMonitoringInternal(startupData)
            }
        }
    }

    // ==================== گام پنجم: آزمایش مصرف‌کننده کند ====================

    fun selectFlowLabMode(mode: FlowLabMode) {
        if (_flowLabState.value.isRunning) return
        _flowLabState.update { it.copy(selectedMode = mode) }
    }

    private fun burstFlow(): Flow<Int> = flow {
        for (value in 1..5) {
            delay(100)
            emit(value)
        }
    }

    fun runFlowLab() {
        if (labJob?.isActive == true) return

        val mode = _flowLabState.value.selectedMode
        val startTime = System.currentTimeMillis()

        _flowLabState.update {
            it.copy(
                isRunning = true,
                logs = emptyList()
            )
        }
        Log.d("FlowLab", "Run started | mode=$mode")

        lateinit var currentLabJob: Job
        currentLabJob = viewModelScope.launch {
            try {
                suspend fun processValue(value: Int, trackCancel: Boolean = false) {
                    appendLabLog(mode, startTime, "START($value)")
                    try {
                        delay(300)
                        appendLabLog(mode, startTime, "DONE($value)")
                    } catch (e: CancellationException) {
                        if (trackCancel) {
                            appendLabLog(mode, startTime, "CANCELLED($value)")
                        }
                        throw e
                    }
                }

                when (mode) {
                    FlowLabMode.NORMAL -> {
                        burstFlow().collect { value ->
                            processValue(value)
                        }
                    }

                    FlowLabMode.BUFFER -> {
                        burstFlow()
                            .buffer()
                            .collect { value ->
                                processValue(value)
                            }
                    }

                    FlowLabMode.CONFLATE -> {
                        burstFlow()
                            .conflate()
                            .collect { value ->
                                processValue(value)
                            }
                    }

                    FlowLabMode.COLLECT_LATEST -> {
                        burstFlow().collectLatest { value ->
                            processValue(value, trackCancel = true)
                        }
                    }
                }
                Log.d("FlowLab", "Run completed | mode=$mode")
            } catch (e: CancellationException) {
                Log.d("FlowLab", "Run cancelled | mode=$mode")
                throw e
            } finally {
                if (labJob === currentLabJob) {
                    labJob = null
                    _flowLabState.update { it.copy(isRunning = false) }
                }
            }
        }

        labJob = currentLabJob
    }

    fun cancelFlowLab() {
        val jobToCancel = labJob ?: return
        if (!jobToCancel.isActive) return

        viewModelScope.launch {
            Log.d("FlowLab", "Cancelling lab job...")
            jobToCancel.cancelAndJoin()
        }
    }

    private fun appendLabLog(mode: FlowLabMode, startTime: Long, message: String) {
        val elapsed = System.currentTimeMillis() - startTime
        val entry = "[+${elapsed}ms] $message"
        Log.d("FlowLab", "mode=$mode | $entry")
        _flowLabState.update { state ->
            val updated = (state.logs + entry).takeLast(MAX_LAB_LOG_LINES)
            state.copy(logs = updated)
        }
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 30
        private const val MAX_LAB_LOG_LINES = 100
    }
}