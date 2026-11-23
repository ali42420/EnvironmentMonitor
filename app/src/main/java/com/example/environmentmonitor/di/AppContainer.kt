package com.example.environmentmonitor.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.environmentmonitor.data.EnvironmentRepository
import com.example.environmentmonitor.data.SimulatedEnvironmentRepository
import com.example.environmentmonitor.ui.MonitorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AppContainer {
    val repository: EnvironmentRepository = SimulatedEnvironmentRepository()
    val workerDispatcher: CoroutineDispatcher = Dispatchers.Default

    val monitorViewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MonitorViewModel::class.java)) {
                return MonitorViewModel(
                    repository = repository,
                    workerDispatcher = workerDispatcher
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}