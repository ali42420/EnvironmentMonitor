package com.example.environmentmonitor

import android.app.Application
import com.example.environmentmonitor.di.AppContainer

class MonitorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}