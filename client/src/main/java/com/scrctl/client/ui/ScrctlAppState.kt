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
import com.scrctl.client.ui.device.remote.RemoteControlScreen
import com.scrctl.client.ui.home.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object DeviceAdd : Screen("device_add")

    object DeviceControl : Screen("control/{deviceId}") {
        fun createRoute(deviceId: String) = "control/$deviceId"
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
                    appState.navigateToRemoteControl(device.id)
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
        
    }
}
