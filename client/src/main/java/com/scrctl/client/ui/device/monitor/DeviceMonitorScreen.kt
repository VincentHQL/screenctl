package com.scrctl.client.ui.device.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.ui.components.DeviceView
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMonitorScreen(
    deviceId: String,
    onBackClick: () -> Unit,
) {
    val viewModel: DeviceMonitorViewModel = hiltViewModel()

    val parsedDeviceId = remember(deviceId) { deviceId.toLongOrNull() }

    LaunchedEffect(deviceId) {
        viewModel.setDeviceId(deviceId)
        viewModel.start()
    }

    val uiState = viewModel.uiState
    val colors = MaterialTheme.colorScheme

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "设备监控") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.retry() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "实时画面",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
						val id = parsedDeviceId
						if (id == null) {
							Text(text = "deviceId 无效: $deviceId", color = colors.onSurfaceVariant)
						} else {
							// DeviceView 在 composable 可见时自动轮询 screencap
							DeviceView(
							screencapProvider = { viewModel.screencapPng(id) },
								modifier = Modifier.fillMaxSize(),
								refreshIntervalMs = 500L,
							)
						}
                        }
                    }
                }
            }

            when (uiState) {
                is DeviceMonitorViewModel.UiState.Loading -> {
                    item {
                        InfoCard(title = "状态", value = "加载中...", subtitle = "设备: $deviceId")
                    }
                }

                is DeviceMonitorViewModel.UiState.Error -> {
                    item {
                        InfoCard(
                            title = "状态",
                            value = "采样失败",
                            subtitle = uiState.message,
                            valueColor = Color(0xFFEF4444),
                        )
                    }
                }

                is DeviceMonitorViewModel.UiState.Ready -> {
                    val m = uiState.metrics

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            InfoCard(
                                title = "CPU",
                                value = m.cpuPercent?.let { "$it%" } ?: "-",
                                subtitle = "更新: ${timeFormatter.format(m.lastUpdated.atZone(ZoneId.systemDefault()))}",
                                modifier = Modifier.weight(1f)
                            )
                            InfoCard(
                                title = "内存",
                                value = m.memPercent?.let { "$it%" } ?: "-",
                                subtitle = m.uptimeText?.let { "运行: $it" } ?: "运行: -",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        val battery = m.battery
                        val line = buildString {
                            if (battery?.level != null) append("${battery.level}%") else append("-")
                            if (!battery?.status.isNullOrBlank()) append(" • ${battery?.status}")
                        }
                        val subtitle = buildString {
                            val temp = battery?.temperatureC
                            val v = battery?.voltageMv
                            if (temp != null) append("温度 ${temp.roundToInt()}°C")
                            if (temp != null && v != null) append(" • ")
                            if (v != null) append("电压 ${v}mV")
                        }.ifBlank { "-" }

                        InfoCard(
                            title = "电池",
                            value = line,
                            subtitle = subtitle,
                        )
                    }

                    item {
                        val net = m.net
                        InfoCard(
                            title = "网络",
                            value = "下行 ${formatRate(net?.rxBytesPerSec)} • 上行 ${formatRate(net?.txBytesPerSec)}",
                            subtitle = "按 /proc/net/dev 统计",
                        )
                    }

                    item {
                        InfoCard(
                            title = "存储",
                            value = m.storage?.text ?: "-",
                            subtitle = "df -h /data",
                        )
                    }

                    item {
                        Text(
                            text = "设备: $deviceId",
                            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = valueColor ?: colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatRate(bytesPerSec: Long?): String {
    if (bytesPerSec == null) return "-"
    val b = bytesPerSec.toDouble()
    val kb = b / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB/s", mb)
        kb >= 1.0 -> String.format("%.0f KB/s", kb)
        else -> String.format("%.0f B/s", b)
    }
}
