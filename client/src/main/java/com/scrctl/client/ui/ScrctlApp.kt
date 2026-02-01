package com.scrctl.client.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable


@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun ScrctlApp() {
    ScrctlNavHost()
}