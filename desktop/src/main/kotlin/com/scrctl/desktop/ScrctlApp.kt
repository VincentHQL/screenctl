package com.scrctl.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scrctl.desktop.model.NavSection
import com.scrctl.desktop.ui.components.TopNavBar
import com.scrctl.desktop.ui.screens.DeviceManagementScreen
import com.scrctl.desktop.ui.theme.ScrctlTheme

@Composable
fun ScrctlApp() {
	val dark = isSystemInDarkTheme()
	var section by remember { mutableStateOf(NavSection.Devices) }

	ScrctlTheme(dark = dark) {
		Surface(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.fillMaxSize()) {
				TopNavBar(
					active = section,
					onSelect = { section = it },
				)
				when (section) {
					NavSection.Devices -> DeviceManagementScreen(modifier = Modifier.weight(1f))
					else -> DeviceManagementScreen(modifier = Modifier.weight(1f))
				}
			}
		}
	}
} 