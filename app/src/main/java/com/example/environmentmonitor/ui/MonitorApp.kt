package com.example.environmentmonitor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.environmentmonitor.R
import com.example.environmentmonitor.ui.screens.DashboardScreen
import com.example.environmentmonitor.ui.screens.FlowLabScreen
import com.example.environmentmonitor.ui.screens.HistoryScreen

// تعریف یکپارچه مسیرها در یک جا
object MonitorRoutes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val FLOW_LAB = "flow-lab"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorApp(
    viewModel: MonitorViewModel
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // جمع‌آوری وضعیت‌ها به صورت آگاه از چرخه حیات
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val flowLabState by viewModel.flowLabState.collectAsStateWithLifecycle()

    // جمع‌آوری رویدادهای گذرا (Snackbar) فقط در یک مصرف‌کننده وابسته به چرخه حیات
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                val message = when (event) {
                    MonitorEvent.MonitoringStarted ->
                        context.getString(R.string.snackbar_monitoring_started)
                    MonitorEvent.MonitoringStopped ->
                        context.getString(R.string.snackbar_monitoring_stopped)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showMainChrome = currentRoute == MonitorRoutes.DASHBOARD || currentRoute == MonitorRoutes.HISTORY

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (showMainChrome) {
                    TopAppBar(
                        title = {
                            val titleRes = if (currentRoute == MonitorRoutes.HISTORY) {
                                R.string.nav_history
                            } else {
                                R.string.app_name
                            }
                            Text(text = stringResource(titleRes))
                        }
                    )
                }
            },
            bottomBar = {
                // نوار پایین فقط در دو تب اصلی دیده شود
                if (showMainChrome) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == MonitorRoutes.DASHBOARD,
                            onClick = {
                                navController.navigate(MonitorRoutes.DASHBOARD) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = stringResource(R.string.cd_dashboard_icon)
                                )
                            },
                            label = { Text(text = stringResource(R.string.nav_dashboard)) }
                        )

                        NavigationBarItem(
                            selected = currentRoute == MonitorRoutes.HISTORY,
                            onClick = {
                                navController.navigate(MonitorRoutes.HISTORY) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.cd_history_icon)
                                )
                            },
                            label = { Text(text = stringResource(R.string.nav_history)) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = MonitorRoutes.DASHBOARD,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(MonitorRoutes.DASHBOARD) {
                    DashboardScreen(
                        uiState = uiState,
                        onRetryStartup = viewModel::loadStartupData,
                        onStartMonitoring = viewModel::startMonitoring,
                        onStopMonitoring = viewModel::stopMonitoring,
                        onTriggerNextError = viewModel::triggerNextTemperatureError,
                        onRetryMonitoring = viewModel::retryMonitoring,
                        onOpenFlowLab = {
                            navController.navigate(MonitorRoutes.FLOW_LAB) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(MonitorRoutes.HISTORY) {
                    HistoryScreen(uiState = uiState)
                }

                composable(MonitorRoutes.FLOW_LAB) {
                    FlowLabScreen(
                        state = flowLabState,
                        onSelectMode = viewModel::selectFlowLabMode,
                        onRun = viewModel::runFlowLab,
                        onCancel = viewModel::cancelFlowLab,
                        onNavigateBack = {
                            navController.popBackStack(
                                route = MonitorRoutes.DASHBOARD,
                                inclusive = false
                            )
                        }
                    )
                }
            }
        }
    }
}