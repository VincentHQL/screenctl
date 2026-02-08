package com.scrctl.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrctl.desktop.model.Device
import com.scrctl.desktop.model.DeviceDensityMode
import com.scrctl.desktop.model.DeviceStatus
import com.scrctl.desktop.ui.theme.ScrctlColors

@Immutable
data class DeviceGridHeader(
	val title: String,
	val total: Int,
	val online: Int,
	val offline: Int,
)

@Composable
fun DeviceGrid(
	header: DeviceGridHeader,
	density: DeviceDensityMode,
	devices: List<Device>,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier) {
		DeviceGridTop(header = header)
		Spacer(Modifier.height(14.dp))
		val minSize = when (density) {
			DeviceDensityMode.Compact -> 160.dp
			DeviceDensityMode.Normal -> 180.dp
			DeviceDensityMode.Comfort -> 220.dp
		}
		LazyVerticalGrid(
			columns = GridCells.Adaptive(minSize),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			items(devices, key = { it.id }) { device ->
				DeviceCard(device = device)
			}
		}
	}
}

@Composable
private fun DeviceGridTop(header: DeviceGridHeader) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = "${header.title} (${header.total})",
			color = Color(0xFF94A3B8),
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.Bold,
		)
		Spacer(Modifier.width(12.dp))
		Pill(text = "在线 (${header.online})", fg = MaterialTheme.colorScheme.primary, bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
		Spacer(Modifier.width(8.dp))
		Pill(text = "离线 (${header.offline})", fg = Color(0xFF94A3B8), bg = Color(0xFF1F2937))
		Spacer(Modifier.weight(1f))
	}
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
	Box(
		modifier = Modifier
			.clip(RoundedCornerShape(999.dp))
			.background(bg)
			.padding(horizontal = 10.dp, vertical = 6.dp),
	) {
		Text(text = text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
	}
}

@Composable
fun DeviceCard(device: Device, modifier: Modifier = Modifier) {
	val shape = RoundedCornerShape(16.dp)
	val border = if (device.status == DeviceStatus.Online) Color(0xFF334155) else Color(0xFF1F2937)
	Surface(
		modifier = modifier
			.aspectRatio(9f / 16f)
			.clip(shape)
			.border(1.dp, border, shape),
		color = Color(0xFF0B1220),
		shape = shape,
		onClick = {},
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			// 预览区域占位：后续可接入 scrcpy 截图流/视频流
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.verticalGradient(
							colors = listOf(Color(0xFF0B1220), Color(0xFF020617)),
						),
					),
			)

			// 顶部渐变信息条
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent),
						),
					)
					.padding(10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				StatusDot(online = device.status == DeviceStatus.Online)
				Spacer(Modifier.width(8.dp))
				Text(
					text = device.name,
					color = Color.White,
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
			}

			// 底部渐变信息条
			Column(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.fillMaxWidth()
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
						),
					)
					.padding(10.dp),
			) {
				Text(
					text = device.currentApp ?: if (device.status == DeviceStatus.Offline) "离线" else "-",
					color = Color(0xFFCBD5E1),
					style = MaterialTheme.typography.labelSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(Modifier.height(6.dp))
				Row(verticalAlignment = Alignment.CenterVertically) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							imageVector = Icons.Filled.BatteryFull,
							contentDescription = null,
							tint = if (device.status == DeviceStatus.Online) Color(0xFF22C55E) else Color(0xFF64748B),
							modifier = Modifier.size(14.dp),
						)
						Spacer(Modifier.width(4.dp))
						Text(
							text = device.batteryPercent?.let { "$it%" } ?: "-",
							color = Color.White,
							style = MaterialTheme.typography.labelSmall,
						)
					}
					Spacer(Modifier.weight(1f))
					Text(
						text = device.status.label,
						color = if (device.status == DeviceStatus.Online) Color(0xFF22C55E) else Color(0xFF94A3B8),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
					)
				}
			}

			// Hover 层：桌面端 hover 事件需要 pointerMoveFilter，这里先用可见按钮占位
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(ScrctlColors.Primary.copy(alpha = 0.0f)),
			) {
				if (device.status == DeviceStatus.Online) {
					Button(
						onClick = {},
						colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ScrctlColors.Primary),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier.align(Alignment.Center),
					) {
						Text("控制设备", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
					}
				}
			}
		}
	}
}

@Composable
private fun StatusDot(online: Boolean) {
	Box(
		modifier = Modifier
			.size(8.dp)
			.clip(RoundedCornerShape(999.dp))
			.background(if (online) Color(0xFF22C55E) else Color(0xFF64748B)),
	)
}
