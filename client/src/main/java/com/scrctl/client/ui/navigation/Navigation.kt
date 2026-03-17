package com.scrctl.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.scrctl.client.ui.app.AppManagerScreen
import com.scrctl.client.ui.device.DeviceAddScreen
import com.scrctl.client.ui.device.DeviceDetailScreen
import com.scrctl.client.ui.file.FileManagerScreen
import com.scrctl.client.ui.home.HomeScreen
import com.scrctl.client.ui.monitor.DeviceMonitorScreen
import com.scrctl.client.ui.remote.RemoteScreen
import com.scrctl.client.ui.terminal.TerminalScreen

/**
 * 应用导航路由定义
 */
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

/**
 * 主导航组件
 */
@Composable
fun ScrctlNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onDeviceClick = { device ->
                    navController.navigate(Screen.DeviceDetail.createRoute(device.id.toString()))
                },
                onAddDevice = {
                    navController.navigate(Screen.DeviceAdd.route)
                },
            )
        }

        composable(Screen.DeviceAdd.route) {
            DeviceAddScreen(
                onBackClick = { navController.popBackStack() },
                onAdded = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.DeviceControl.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            RemoteScreen(
                deviceId = deviceId,
                onExit = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")?.toLongOrNull() ?: return@composable
            DeviceDetailScreen(
                deviceId = deviceId,
                onBackClick = { navController.popBackStack() },
                onEnterControl = {
                    navController.navigate(Screen.DeviceControl.createRoute(deviceId.toString()))
                },
                onFileManager = {
                    navController.navigate(Screen.FileManager.createRoute(deviceId.toString()))
                },
                onAppManager = {
                    navController.navigate(Screen.AppManager.createRoute(deviceId.toString()))
                },
                onDeviceMonitor = {
                    navController.navigate(Screen.DeviceMonitor.createRoute(deviceId.toString()))
                },
                onTerminal = {
                    navController.navigate(Screen.Terminal.createRoute(deviceId.toString()))
                },
            )
        }

        composable(
            route = Screen.FileManager.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")?.toLongOrNull() ?: return@composable
            FileManagerScreen(
                deviceId = deviceId,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.AppManager.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")?.toLongOrNull() ?: return@composable
            AppManagerScreen(
                deviceId = deviceId,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.DeviceMonitor.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")?.toLongOrNull() ?: return@composable
            DeviceMonitorScreen(
                deviceId = deviceId,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")?.toLongOrNull() ?: return@composable
            TerminalScreen(
                deviceId = deviceId,
                onExit = { navController.popBackStack() },
            )
        }
    }
}