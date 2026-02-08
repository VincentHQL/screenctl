package com.scrctl.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scrctl.desktop.model.DeviceStatus
import com.scrctl.desktop.model.DeviceDensityMode
import com.scrctl.desktop.ui.components.DeviceActionBar
import com.scrctl.desktop.ui.components.DeviceGrid
import com.scrctl.desktop.ui.components.DeviceGridHeader
import com.scrctl.desktop.ui.components.FloatingProgressToast
import com.scrctl.desktop.ui.components.FooterStatusBar
import com.scrctl.desktop.ui.components.SideGroupBar
import com.scrctl.desktop.ui.sample.SampleData
import com.scrctl.desktop.system.HostSystemStatsSampler
import com.scrctl.desktop.ui.theme.ScrctlColors

@Composable
fun DeviceManagementScreen(modifier: Modifier = Modifier) {
	var selectedGroupId by remember { mutableStateOf("all") }
	var query by remember { mutableStateOf("") }
	var density by remember { mutableStateOf(DeviceDensityMode.Normal) }
	val statsSampler = remember { HostSystemStatsSampler() }
	val liveStats by remember(statsSampler) {
		statsSampler.statsFlow(pollIntervalMs = 1000L)
	}.collectAsState(initial = SampleData.stats)

	// 示例过滤：后续替换为真实设备列表/分组过滤
	val devices = remember(query, selectedGroupId) {
		SampleData.devices.filter { it.name.contains(query, ignoreCase = true) }
	}
	val online = devices.count { it.status == DeviceStatus.Online }
	val offline = devices.size - online

	Box(modifier = modifier.fillMaxSize().background(ScrctlColors.DarkBackground)) {
		Row(modifier = Modifier.fillMaxSize()) {
			SideGroupBar(
				groups = SampleData.groups,
				selectedGroupId = selectedGroupId,
				onSelect = { selectedGroupId = it },
			)
			Column(modifier = Modifier.weight(1f).fillMaxSize()) {
				DeviceActionBar(
					query = query,
					onQueryChange = { query = it },
					onRunScript = {},
				)
				Column(modifier = Modifier.weight(1f).padding(16.dp)) {
					DeviceGrid(
						header = DeviceGridHeader(
							title = "所有设备",
							total = devices.size,
							online = online,
							offline = offline,
						),
						density = density,
						devices = devices,
					)
				}
				FooterStatusBar(stats = liveStats)
			}
		}

		FloatingProgressToast(
			progress = SampleData.progress,
			modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 56.dp),
		)
		// 预留：密度切换（原型右上角）
		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 76.dp, end = 18.dp)
				.background(Color.Transparent),
		)
	}
}
