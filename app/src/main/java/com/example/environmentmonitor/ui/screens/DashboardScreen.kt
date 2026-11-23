package com.example.environmentmonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.environmentmonitor.R
import com.example.environmentmonitor.ui.MonitorUiState
import com.example.environmentmonitor.ui.MonitoringStatus
import com.example.environmentmonitor.ui.StartupStatus
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: MonitorUiState,
    onRetryStartup: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onTriggerNextError: () -> Unit,
    onRetryMonitoring: () -> Unit,
    onOpenFlowLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (uiState.startupStatus) {
            StartupStatus.LOADING -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.startup_loading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            StartupStatus.ERROR -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.startup_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = uiState.startupErrorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        uiState.startupDurationMs?.let { duration ->
                            Text(
                                text = stringResource(R.string.startup_duration_label, duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(onClick = onRetryStartup) {
                            Text(text = stringResource(R.string.btn_retry))
                        }
                    }
                }
            }

            StartupStatus.READY -> {
                val startupData = uiState.startupData
                if (startupData != null) {
                    // کارت اطلاعات ایستگاه و زمان آماده‌سازی
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.station_label,
                                    startupData.station.name,
                                    startupData.station.id
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    R.string.limits_label,
                                    startupData.limits.maxTempC,
                                    startupData.limits.maxHumidity
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            uiState.startupDurationMs?.let { duration ->
                                Text(
                                    text = stringResource(R.string.startup_duration_label, duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // کارت نمایش زنده دما، رطوبت و وضعیت عادی / هشدار
                val reading = uiState.latestReading
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monitoringStatusText(uiState.monitoringStatus),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (reading != null) {
                                StatusBadge(isAlert = reading.isAlert)
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricColumn(
                                label = stringResource(R.string.temperature_label),
                                value = reading?.let {
                                    String.format(Locale.US, "%.1f °C", it.tempC)
                                } ?: stringResource(R.string.placeholder_value)
                            )
                            MetricColumn(
                                label = stringResource(R.string.humidity_label),
                                value = reading?.let {
                                    "${it.humidity} %"
                                } ?: stringResource(R.string.placeholder_value)
                            )
                        }

                        if (reading == null) {
                            Text(
                                text = stringResource(R.string.waiting_first_reading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // کارت خطای قابل بازیابی در حین پایش
                if (uiState.monitoringStatus == MonitoringStatus.ERROR) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.monitoringErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = onRetryMonitoring) {
                                Text(text = stringResource(R.string.btn_retry))
                            }
                        }
                    }
                }

                // کنترل‌های شروع / توقف و شبیه‌سازی خطا
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isRunning) {
                        Button(
                            onClick = onStopMonitoring,
                            enabled = uiState.isStartStopEnabled,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(text = stringResource(R.string.btn_stop))
                        }
                    } else {
                        Button(
                            onClick = onStartMonitoring,
                            enabled = uiState.isStartStopEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.btn_start))
                        }
                    }

                    OutlinedButton(
                        onClick = onTriggerNextError,
                        enabled = uiState.isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.btn_trigger_error))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // دکمه ورود به صفحه آزمایش جریان (همیشه در دسترس)
        OutlinedButton(
            onClick = onOpenFlowLab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.btn_open_flow_lab))
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusBadge(isAlert: Boolean) {
    val containerColor = if (isAlert) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isAlert) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAlert) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = stringResource(
                    if (isAlert) R.string.cd_status_alert else R.string.cd_status_normal
                ),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(if (isAlert) R.string.status_alert else R.string.status_normal),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun monitoringStatusText(status: MonitoringStatus): String {
    return when (status) {
        MonitoringStatus.IDLE -> stringResource(R.string.monitoring_state_idle)
        MonitoringStatus.RUNNING -> stringResource(R.string.monitoring_state_running)
        MonitoringStatus.STOPPING -> stringResource(R.string.monitoring_state_stopping)
        MonitoringStatus.STOPPED -> stringResource(R.string.monitoring_state_stopped)
        MonitoringStatus.ERROR -> stringResource(R.string.monitoring_state_error)
    }
}