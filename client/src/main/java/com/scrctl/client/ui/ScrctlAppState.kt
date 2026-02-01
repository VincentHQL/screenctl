package com.scrctl.client.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scrctl.client.ui.device.DeviceAddScreen
import com.scrctl.client.ui.device.DeviceDetailScreen
import com.scrctl.client.ui.device.app.AppManagerScreen
import com.scrctl.client.ui.device.file.FileManagerScreen
import com.scrctl.client.ui.device.monitor.DeviceMonitorScreen
import com.scrctl.client.ui.home.HomeScreen
import com.scrctl.client.ui.remote.RemoteControlScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object DeviceAdd : Screen("device_add")

    object DeviceDetail : Screen("device_detail/{deviceId}") {
        fun createRoute(deviceId: Long) = "device_detail/$deviceId"
    }

    object DeviceControl : Screen("device_control/{deviceId}") {
        fun createRoute(deviceId: String) = "device_control/$deviceId"
    }

    object FileManager : Screen("file_manager/{deviceId}") {
        fun createRoute(deviceId: Long) = "file_manager/$deviceId"
    }

    object AppManager : Screen("app_manager/{deviceId}") {
        fun createRoute(deviceId: Long) = "app_manager/$deviceId"
    }

    object DeviceMonitor : Screen("device_monitor/{deviceId}") {
        fun createRoute(deviceId: Long) = "device_monitor/$deviceId"
    }
}

@Composable
fun rememberScrctlAppState(
    navController: NavHostController = rememberNavController(),
    context: Context = LocalContext.current
) = remember(navController, context) {
    ScrctlAppState(navController, context)
}

class ScrctlAppState(
    val navController: NavHostController,
    private val context: Context
) {

    fun navigateToDeviceDetail(deviceId: Long) {
        navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
    }

    fun navigateToRemoteControl(deviceId: String) {
        navController.navigate(Screen.DeviceControl.createRoute(deviceId))
    }

    fun navigateToFileManager(deviceId: Long) {
        navController.navigate(Screen.FileManager.createRoute(deviceId))
    }

    fun navigateToAppManager(deviceId: Long) {
        navController.navigate(Screen.AppManager.createRoute(deviceId))
    }

    fun navigateToDeviceMonitor(deviceId: Long) {
        navController.navigate(Screen.DeviceMonitor.createRoute(deviceId))
    }

    fun navigateToDeviceAdd() {
        navController.navigate(Screen.DeviceAdd.route)
    }

    fun navigateBack() {
        navController.popBackStack()
    }

}

@Composable
fun ScrctlNavHost(
    appState: ScrctlAppState = rememberScrctlAppState()
) {
    NavHost(
        navController = appState.navController,
        startDestination = Screen.Home.route
    ) {
        // 主页 - 设备列表
        composable(Screen.Home.route) {
            HomeScreen(
                onDeviceClick = { device ->
                    appState.navigateToDeviceDetail(device.id)
                },
                onAddDevice = { appState.navigateToDeviceAdd() }
            )
        }
        
        // 添加设备页
        composable(Screen.DeviceAdd.route) {
            DeviceAddScreen(
                onBackClick = { appState.navigateBack() },
                onAdded = { appState.navigateBack() }
            )
        }

        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")?.toLongOrNull() ?: return@composable
            DeviceDetailScreen(
                deviceId = deviceId,
                onBackClick = { appState.navigateBack() },
                onEnterControl = { appState.navigateToRemoteControl(deviceId.toString()) },
                onFileManager = { appState.navigateToFileManager(deviceId) },
                onAppManager = { appState.navigateToAppManager(deviceId) },
                onDeviceMonitor = { appState.navigateToDeviceMonitor(deviceId) },
            )
        }

        // 设备操控页（占位实现，避免点击设备后导航崩溃）
        composable(
            route = Screen.DeviceControl.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            RemoteControlScreen(
                deviceId = deviceId,
                onExit = { appState.navigateBack() }
            )
        }

        composable(
            route = Screen.FileManager.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            FileManagerScreen(deviceId = deviceId, onBackClick = { appState.navigateBack() })
        }

        composable(
            route = Screen.AppManager.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            AppManagerScreen(deviceId = deviceId, onBackClick = { appState.navigateBack() })
        }

        composable(
            route = Screen.DeviceMonitor.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            DeviceMonitorScreen(deviceId = deviceId, onBackClick = { appState.navigateBack() })
        }
        
    }
}
