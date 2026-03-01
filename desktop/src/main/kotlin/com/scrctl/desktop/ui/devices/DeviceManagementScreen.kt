package com.scrctl.desktop.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrctl.desktop.model.Device
import com.scrctl.desktop.model.DeviceStatus
import com.scrctl.desktop.model.DeviceDensityMode
import com.scrctl.desktop.model.DeviceGroup
import com.scrctl.desktop.ui.sample.SampleData
import com.scrctl.desktop.ui.theme.ScrctlColors
import kotlin.collections.forEach

@Composable
fun DeviceManagementScreen(modifier: Modifier = Modifier) {
	var selectedGroupId by remember { mutableStateOf("all") }
	var query by remember { mutableStateOf("") }
	var density by remember { mutableStateOf(DeviceDensityMode.Normal) }

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
			}
		}
	}
}


@Composable
fun SideGroupBar(
	groups: List<DeviceGroup>,
	selectedGroupId: String,
	onSelect: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val border = Color(0xFF1F2937)
	Column(
		modifier = modifier
			.width(240.dp)
			.fillMaxHeight()
			.background(ScrctlColors.DarkSurface)
			.border(1.dp, border),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = "设备分组",
					color = Color(0xFF94A3B8),
					fontWeight = FontWeight.Bold,
					style = MaterialTheme.typography.labelSmall,
				)
				Spacer(modifier = Modifier.weight(1f))
				IconButton(onClick = {}) {
					Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color(0xFF94A3B8))
				}
			}
			Spacer(Modifier.height(8.dp))
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				groups.forEach { group ->
					GroupRow(
						group = group,
						selected = group.id == selectedGroupId,
						onClick = { onSelect(group.id) },
					)
				}
			}

			Spacer(Modifier.height(18.dp))
			Text(
				text = "快捷操作",
				color = Color(0xFF94A3B8),
				fontWeight = FontWeight.Bold,
				style = MaterialTheme.typography.labelSmall,
			)
			Spacer(Modifier.height(10.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				QuickActionCard(text = "全量同步", modifier = Modifier.weight(1f))
				QuickActionCard(text = "清理缓存", modifier = Modifier.weight(1f))
			}
		}

		Spacer(Modifier.weight(1f))
		CapacityFooter(used = 48, total = 100)
	}
}

@Composable
private fun GroupRow(group: DeviceGroup, selected: Boolean, onClick: () -> Unit) {
	val bg = if (selected) Color(0xFF1F2937).copy(alpha = 0.5f) else Color.Transparent
	val border = if (selected) Color(0xFF334155).copy(alpha = 0.5f) else Color.Transparent
	val text = if (selected) Color.White else Color(0xFF94A3B8)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.background(bg)
			.border(1.dp, border, RoundedCornerShape(10.dp))
			.clickable(
				indication = null,
				interactionSource = MutableInteractionSource(),
				onClick = onClick,
			)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = group.name,
			color = text,
			style = MaterialTheme.typography.bodySmall,
			fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		Spacer(Modifier.weight(1f))
		Box(
			modifier = Modifier
				.clip(RoundedCornerShape(6.dp))
				.background(Color(0xFF334155))
				.padding(horizontal = 6.dp, vertical = 2.dp),
		) {
			Text(
				text = "${group.onlineCount}/${group.totalCount}",
				color = Color.White,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
			)
		}
	}
}



@Composable
private fun QuickActionCard(text: String, modifier: Modifier = Modifier) {
	Surface(
		modifier = modifier.height(70.dp),
		shape = RoundedCornerShape(12.dp),
		color = Color(0xFF0F172A).copy(alpha = 0.15f),
		onClick = {},
	) {
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			Text(text = text, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
		}
	}
}

@Composable
private fun CapacityFooter(used: Int, total: Int) {
	val border = Color(0xFF1F2937)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, border)
			.padding(16.dp),
	) {
		Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1F2937).copy(alpha = 0.3f)) {
			Column(modifier = Modifier.padding(12.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text("总容量", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
					Spacer(Modifier.weight(1f))
					Text("$used/$total", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
				}
				Spacer(Modifier.height(8.dp))
				LinearProgressIndicator(
					progress = used.toFloat() / total.toFloat(),
					color = MaterialTheme.colorScheme.primary,
					trackColor = Color(0xFF334155),
					modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
				)
			}
		}
	}
}


@Composable
fun DeviceActionBar(
	query: String,
	onQueryChange: (String) -> Unit,
	onRunScript: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
	val divider = if (isDark) Color(0xFF1F2937) else Color(0xFFE2E8F0)
	val container = Color.Transparent
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(56.dp)
			.background(container)
			.padding(horizontal = 16.dp),
	) {
		Row(
			modifier = Modifier.fillMaxSize(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				ActionPill(text = "全选", icon = Icons.Filled.SelectAll, variant = ActionPillVariant.Neutral)
				ActionPill(text = "屏幕同步", icon = Icons.Filled.Sync, variant = ActionPillVariant.Primary)
				ActionPill(text = "批量安装", icon = Icons.Filled.FileDownload, variant = ActionPillVariant.Neutral)
				ActionPill(text = "全部重启", icon = Icons.Filled.RestartAlt, variant = ActionPillVariant.Neutral)
			}
			Box(
				modifier = Modifier
					.height(16.dp)
					.width(1.dp)
					.background(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
			)
			ActionPill(text = "运行脚本", icon = Icons.Filled.PlayArrow, variant = ActionPillVariant.Success, onClick = onRunScript)
			Spacer(Modifier.weight(1f))
			SearchField(
				value = query,
				onValueChange = onQueryChange,
				modifier = Modifier.width(192.dp),
			)
		}
		Box(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.height(1.dp)
				.background(divider),
		)
	}
}

@Composable
private fun SearchField(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
	val shape = RoundedCornerShape(12.dp)
	val bg = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9) // slate-900 / slate-100
	val placeholder = Color(0xFF64748B) // slate-500
	val focusRing = MaterialTheme.colorScheme.primary
	val textStyle: TextStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
	val focusRequester = FocusRequester()
	var focused = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

	Row(
		modifier = modifier
			.height(36.dp)
			.clip(shape)
			.background(bg)
			.border(width = if (focused.value) 2.dp else 0.dp, color = focusRing, shape = shape)
			.padding(start = 12.dp, end = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(Icons.Filled.Search, contentDescription = null, tint = placeholder, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(8.dp))
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			singleLine = true,
			textStyle = textStyle,
			cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
			modifier = Modifier
				.weight(1f)
				.onFocusChanged { focused.value = it.isFocused },
			decorationBox = { innerTextField ->
				Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
					if (value.isEmpty()) {
						Text("搜索设备...", style = textStyle, color = placeholder)
					}
					innerTextField()
				}
			},
		)
	}
}

private enum class ActionPillVariant { Neutral, Primary, Success }

@Composable
private fun ActionPill(
	text: String,
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	variant: ActionPillVariant,
	onClick: () -> Unit = {},
) {
	val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
	val neutralBg = if (isDark) Color(0xFF1F2937) else Color(0xFFE2E8F0)
	val neutralFg = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
	val (bg, fg) = when (variant) {
		ActionPillVariant.Neutral -> neutralBg to neutralFg
		ActionPillVariant.Primary -> MaterialTheme.colorScheme.primary to Color.White
		ActionPillVariant.Success -> Color(0xFF059669) to Color.White
	}
	Button(
		onClick = onClick,
		shape = RoundedCornerShape(10.dp),
		colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
		contentPadding = ButtonDefaults.ContentPadding,
		modifier = Modifier.height(34.dp),
	) {
		Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(6.dp))
		Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
	}
}


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
