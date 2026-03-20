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
import com.scrctl.client.ui.device.remote.RemoteControlScreen
import com.scrctl.client.ui.device.terminal.TerminalScreen
import com.scrctl.client.ui.home.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object DeviceAdd : Screen("device_add")

    object DeviceControl : Screen("control/{deviceId}") {
        fun createRoute(deviceId: String) = "control/$deviceId"
    }

    object DeviceDetail : Screen("detail/{deviceId}") {
        fun createRoute(deviceId: String) = "detail/$deviceId"
    }

    object FileManager : Screen("file_manager/{deviceId}") {
        fun createRoute(deviceId: String) = "file_manager/$deviceId"
    }

    object AppManager : Screen("app_manager/{deviceId}") {
        fun createRoute(deviceId: String) = "app_manager/$deviceId"
    }

    object DeviceMonitor : Screen("device_monitor/{deviceId}") {
        fun createRoute(deviceId: String) = "device_monitor/$deviceId"
    }

    object Terminal : Screen("terminal/{deviceId}") {
        fun createRoute(deviceId: String) = "terminal/$deviceId"
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

    fun navigateToRemoteControl(deviceId: Long) {
        navController.navigate(Screen.DeviceControl.createRoute(deviceId.toString()))
    }

    fun navigateToDeviceDetail(deviceId: Long) {
        navController.navigate(Screen.DeviceDetail.createRoute(deviceId.toString()))
    }

    fun navigateToFileManager(deviceId: Long) {
        navController.navigate(Screen.FileManager.createRoute(deviceId.toString()))
    }

    fun navigateToAppManager(deviceId: Long) {
        navController.navigate(Screen.AppManager.createRoute(deviceId.toString()))
    }

    fun navigateToDeviceMonitor(deviceId: Long) {
        navController.navigate(Screen.DeviceMonitor.createRoute(deviceId.toString()))
    }

    fun navigateToTerminal(deviceId: Long) {
        navController.navigate(Screen.Terminal.createRoute(deviceId.toString()))
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

        // 设备详情
        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            DeviceDetailScreen(
                deviceId = deviceId.toLong(),
                onBackClick = { appState.navigateBack() },
                onEnterControl = { appState.navigateToRemoteControl(deviceId.toLong()) },
                onFileManager = { appState.navigateToFileManager(deviceId.toLong()) },
                onAppManager = { appState.navigateToAppManager(deviceId.toLong()) },
                onDeviceMonitor = { appState.navigateToDeviceMonitor(deviceId.toLong()) },
                onTerminal = { appState.navigateToTerminal(deviceId.toLong()) }
            )
        }

        // 文件管理
        composable(
            route = Screen.FileManager.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            FileManagerScreen(
                deviceId = deviceId.toLong(),
                onBackClick = { appState.navigateBack() }
            )
        }

        // 应用管理
        composable(
            route = Screen.AppManager.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            AppManagerScreen(
                deviceId = deviceId.toLong(),
                onBackClick = { appState.navigateBack() }
            )
        }

        // 设备监控
        composable(
            route = Screen.DeviceMonitor.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            DeviceMonitorScreen(
                deviceId = deviceId.toLong(),
                onBackClick = { appState.navigateBack() }
            )
        }

        // 设备操控页
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

        // 终端
        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            TerminalScreen(
                deviceId = deviceId.toLong(),
                onBackClick = { appState.navigateBack() }
            )
        }
        
    }
}
