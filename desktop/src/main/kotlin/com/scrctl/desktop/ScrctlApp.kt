package com.scrctl.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scrctl.desktop.model.NavSection
import com.scrctl.desktop.system.HostSystemStatsSampler
import com.scrctl.desktop.ui.components.TopNavBar
import com.scrctl.desktop.ui.components.FooterStatusBar
import com.scrctl.desktop.ui.devices.DeviceManagementScreen
import com.scrctl.desktop.ui.sample.SampleData
import com.scrctl.desktop.ui.theme.ScrctlTheme

@Composable
fun ScrctlApp() {
	val dark = isSystemInDarkTheme()
	var section by remember { mutableStateOf(NavSection.Devices) }
	val statsSampler = remember { HostSystemStatsSampler() }
	val liveStats by remember(statsSampler) {
		statsSampler.statsFlow(pollIntervalMs = 1000L)
	}.collectAsState(initial = SampleData.stats)

	ScrctlTheme(dark = dark) {
		Surface(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.fillMaxSize()) {
				TopNavBar(
					active = section,
					onSelect = { section = it },
				)
				Box(modifier = Modifier.weight(1f)) {
					when (section) {
						NavSection.Devices -> DeviceManagementScreen(modifier = Modifier.fillMaxSize())
						NavSection.Tasks -> DeviceManagementScreen(modifier = Modifier.fillMaxSize())
						else -> DeviceManagementScreen(modifier = Modifier.fillMaxSize())
					}
				}
				FooterStatusBar(stats = liveStats)
			}
		}
	}
} 