package com.scrctl.client.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.scrctl.client.ui.navigation.ScrctlNavHost

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun ScrctlApp() {
    val navController = rememberNavController()
    ScrctlNavHost(navController)
}