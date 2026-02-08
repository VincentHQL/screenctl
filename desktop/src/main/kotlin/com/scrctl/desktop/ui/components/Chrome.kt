package com.scrctl.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrctl.desktop.model.BatchProgress
import com.scrctl.desktop.model.DeviceGroup
import com.scrctl.desktop.model.NavSection
import com.scrctl.desktop.model.SystemStats
import com.scrctl.desktop.ui.theme.ScrctlColors

@Composable
fun TopNavBar(
	active: NavSection,
	onSelect: (NavSection) -> Unit,
	modifier: Modifier = Modifier,
) {
	val barBg = Color(0xFF111318)
	val border = Color(0xFF1F2937)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(56.dp)
			.background(barBg)
			.border(width = 1.dp, color = border),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Box(
				modifier = Modifier
					.size(28.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(MaterialTheme.colorScheme.primary),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = Icons.Filled.GridView,
					contentDescription = null,
					tint = Color.White,
					modifier = Modifier.size(18.dp),
				)
			}
			Text(
				text = "群控管理系统",
				color = Color.White,
				fontWeight = FontWeight.Bold,
				style = MaterialTheme.typography.labelLarge,
			)
		}

		Row(
			modifier = Modifier.weight(1f),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			NavPill(
				selected = active == NavSection.Devices,
				text = NavSection.Devices.title,
				icon = Icons.Filled.DevicesOther,
				onClick = { onSelect(NavSection.Devices) },
			)
			NavPill(
				selected = active == NavSection.Tasks,
				text = NavSection.Tasks.title,
				icon = Icons.Filled.TaskAlt,
				onClick = { onSelect(NavSection.Tasks) },
			)
			NavPill(
				selected = active == NavSection.Scripts,
				text = NavSection.Scripts.title,
				icon = Icons.Filled.Terminal,
				onClick = { onSelect(NavSection.Scripts) },
			)
			NavPill(
				selected = active == NavSection.Groups,
				text = NavSection.Groups.title,
				icon = Icons.Filled.GroupWork,
				onClick = { onSelect(NavSection.Groups) },
			)
			NavPill(
				selected = active == NavSection.Logs,
				text = NavSection.Logs.title,
				icon = Icons.Filled.HistoryEdu,
				onClick = { onSelect(NavSection.Logs) },
			)
			NavPill(
				selected = active == NavSection.Settings,
				text = NavSection.Settings.title,
				icon = Icons.Filled.Settings,
				onClick = { onSelect(NavSection.Settings) },
			)
		}
	}
}

@Composable
private fun NavPill(
	selected: Boolean,
	text: String,
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	onClick: () -> Unit,
) {
	val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
	val fg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
	val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
	Row(
		modifier = Modifier
			.clip(RoundedCornerShape(8.dp))
			.background(bg)
			.border(1.dp, border, RoundedCornerShape(8.dp))
			.clickable(
				indication = null,
				interactionSource = MutableInteractionSource(),
				onClick = onClick,
			)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
		Text(text = text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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

@Composable
fun FooterStatusBar(stats: SystemStats, modifier: Modifier = Modifier) {
	val border = Color(0xFF1F2937)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(32.dp)
			.background(Color.White.copy(alpha = 0.02f))
			.border(1.dp, border)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			Text("CPU 使用率", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
			Box(modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF334155))) {
				Box(
					modifier = Modifier
						.fillMaxHeight()
						.fillMaxWidth(stats.cpuPercent / 100f)
						.background(Color(0xFF22C55E)),
				)
			}
			Text("${stats.cpuPercent}%", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
			Spacer(Modifier.width(14.dp))
			Text(
				"网络 上行/下行",
				color = Color(0xFF94A3B8),
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
			)
			Text(
				"${"%.1f".format(stats.uploadMbps)} MB/s | ${"%.1f".format(stats.downloadMbps)} MB/s",
				color = MaterialTheme.colorScheme.primary,
				style = MaterialTheme.typography.labelSmall,
			)
		}
		Spacer(Modifier.weight(1f))
		Text(stats.version, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
		Spacer(Modifier.width(10.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF22C55E)))
			Spacer(Modifier.width(6.dp))
			Text(stats.healthLabel, color = Color(0xFF22C55E), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
		}
	}
}

@Composable
fun FloatingProgressToast(progress: BatchProgress, modifier: Modifier = Modifier) {
	Surface(
		modifier = modifier
			.width(300.dp),
		shape = RoundedCornerShape(16.dp),
		color = ScrctlColors.DarkPanel,
		tonalElevation = 6.dp,
		shadowElevation = 10.dp,
	) {
		Column(modifier = Modifier.padding(12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(
					modifier = Modifier
						.size(32.dp)
						.clip(RoundedCornerShape(10.dp))
						.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = Icons.Filled.TaskAlt,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(18.dp),
					)
				}
				Spacer(Modifier.width(10.dp))
				Column(modifier = Modifier.weight(1f)) {
					Text(progress.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
					Text(progress.subtitle, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
				}
				IconButton(onClick = {}) {
					Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color(0xFF94A3B8))
				}
			}
			Spacer(Modifier.height(10.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = "${progress.percent}%",
					color = MaterialTheme.colorScheme.primary,
					fontWeight = FontWeight.Bold,
					style = MaterialTheme.typography.labelSmall,
				)
				Spacer(Modifier.weight(1f))
			}
			Spacer(Modifier.height(6.dp))
			LinearProgressIndicator(
				progress = progress.percent / 100f,
				color = MaterialTheme.colorScheme.primary,
				trackColor = Color(0xFF334155),
				modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
			)
		}
	}
}
