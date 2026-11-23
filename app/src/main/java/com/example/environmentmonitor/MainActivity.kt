package com.example.environmentmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.environmentmonitor.ui.MonitorApp
import com.example.environmentmonitor.ui.MonitorViewModel
import com.example.environmentmonitor.ui.theme.EnvironmentMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as MonitorApplication).container

        setContent {
            EnvironmentMonitorTheme {
                val sharedViewModel: MonitorViewModel = viewModel(
                    factory = appContainer.monitorViewModelFactory
                )
                MonitorApp(viewModel = sharedViewModel)
            }
        }
    }
}