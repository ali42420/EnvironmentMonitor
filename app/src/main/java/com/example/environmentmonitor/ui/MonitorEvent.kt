package com.example.environmentmonitor.ui

sealed interface MonitorEvent {
    data object MonitoringStarted : MonitorEvent
    data object MonitoringStopped : MonitorEvent
}