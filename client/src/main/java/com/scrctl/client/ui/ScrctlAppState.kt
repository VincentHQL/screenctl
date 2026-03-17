package com.scrctl.client.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.scrctl.client.ui.navigation.ScrctlNavHost
import com.scrctl.client.ui.navigation.Screen

@Composable
fun rememberScrctlAppState(
    navController: NavHostController = rememberNavController(),
    context: Context = LocalContext.current
) = remember(navController, context) {
    ScrctlAppState(navController)
}

class ScrctlAppState(
    val navController: NavHostController
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

    fun navigateToDeviceAdd() {
        navController.navigate(Screen.DeviceAdd.route)
    }

    fun navigateBack() {
        navController.popBackStack()
    }

}
