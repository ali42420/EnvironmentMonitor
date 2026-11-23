package com.example.environmentmonitor.ui

enum class FlowLabMode {
    NORMAL,
    BUFFER,
    CONFLATE,
    COLLECT_LATEST
}

data class FlowLabState(
    val selectedMode: FlowLabMode = FlowLabMode.NORMAL,
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList()
)