package com.scrctl.client.ui.device

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.core.devicemanager.DeviceConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
	deviceId: Long,
	onBackClick: () -> Unit,
	onEnterControl: () -> Unit,
	onFileManager: () -> Unit,
	onAppManager: () -> Unit,
	onDeviceMonitor: () -> Unit,
	viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
	LaunchedEffect(deviceId) {
		viewModel.load(deviceId)
	}

	val device = viewModel.device
	val group = viewModel.group
	val batteryPercent = viewModel.batteryPercent
	val batteryLoading = viewModel.batteryLoading

	val background = MaterialTheme.colorScheme.background
	val surface = MaterialTheme.colorScheme.surface
	val outline = MaterialTheme.colorScheme.outlineVariant
	val primary = MaterialTheme.colorScheme.primary
	val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	var showMoreMenu by remember { mutableStateOf(false) }
	var showDeleteConfirm by remember { mutableStateOf(false) }
	var isDeleting by remember { mutableStateOf(false) }

	val title = device?.name?.takeIf { it.isNotBlank() } ?: "设备详情"
	val connectModeText = when (device?.connectMode) {
		1 -> "本地直连"
		2 -> "无线调试"
		else -> "-"
	}

	val connectionState = device?.connectionState ?: DeviceConnectionState.DISCONNECTED.name
	val isOnline = connectionState == DeviceConnectionState.CONNECTED.name
	val onlineText = when (connectionState) {
		DeviceConnectionState.CONNECTED.name -> "在线"
		DeviceConnectionState.CONNECTING.name -> "连接中"
		DeviceConnectionState.ERROR.name -> "异常"
		else -> "离线"
	}
	val batteryText = when {
		batteryLoading -> "电池 获取中"
		batteryPercent != null -> "电池 ${batteryPercent}%"
		else -> "电池 -"
	}
	val statusColor = if (isOnline) primary else onSurfaceVariant

	val groupName = group?.name ?: "-"

	Scaffold(
		containerColor = background,
		snackbarHost = { SnackbarHost(snackbarHostState) },
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "设备详情",
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				},
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
							contentDescription = "返回",
						)
					}
				},
				actions = {
				Box {
					IconButton(onClick = { showMoreMenu = true }) {
						Icon(
							imageVector = Icons.Filled.MoreHoriz,
							contentDescription = "更多",
						)
					}
					DropdownMenu(
						expanded = showMoreMenu,
						onDismissRequest = { showMoreMenu = false },
					) {
						DropdownMenuItem(
							text = { Text("删除设备") },
							onClick = {
								showMoreMenu = false
								showDeleteConfirm = true
							},
						)
					}
				}
				},
				colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
				modifier = Modifier.statusBarsPadding()
			)
		}
	) { padding ->
		if (showDeleteConfirm) {
			AlertDialog(
				onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
				title = { Text("确认删除") },
				text = { Text("删除后不可恢复，确定要删除该设备吗？") },
				confirmButton = {
					TextButton(
						enabled = !isDeleting,
						onClick = {
							val d = device ?: run {
								showDeleteConfirm = false
								return@TextButton
							}
							isDeleting = true
							scope.launch {
								val result = viewModel.deleteDevice(d.id)
								isDeleting = false
								if (result.isSuccess) {
									showDeleteConfirm = false
									onBackClick()
								} else {
									snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "删除失败")
								}
							}
						},
					) { Text("删除") }
				},
				dismissButton = {
					TextButton(enabled = !isDeleting, onClick = { showDeleteConfirm = false }) { Text("取消") }
				}
			)
		}

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.verticalScroll(rememberScrollState())
		) {
			// Hero (device title + online status)
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
					.padding(top = 10.dp)
					.background(
						brush = Brush.verticalGradient(
							colors = listOf(
								primary.copy(alpha = 0.20f),
								Color.Transparent
							)
						),
						shape = RoundedCornerShape(16.dp)
					)
					.padding(16.dp)
			) {
				Column {
					Text(
						text = title,
						fontSize = 28.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onBackground,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Spacer(modifier = Modifier.height(6.dp))
					Row(verticalAlignment = Alignment.CenterVertically) {
						PulseDot(color = statusColor)
						Spacer(modifier = Modifier.size(8.dp))
						Text(
							text = "$onlineText | $batteryText",
							color = statusColor,
							fontSize = 13.sp,
							fontWeight = FontWeight.Medium
						)
					}
					Spacer(modifier = Modifier.height(6.dp))
					Text(
						text = "设备 ID：$deviceId",
						color = onSurfaceVariant,
						fontSize = 12.sp,
					)
				}
			}

			SectionHeader(text = "快捷操作")
			QuickActionsGrid(
				onEnterControl = onEnterControl,
				onFileManager = onFileManager,
				onAppManager = onAppManager,
				onDeviceMonitor = onDeviceMonitor,
			)

			SectionHeader(text = "设备信息")
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			) {
				InfoRow(label = "分组", value = groupName, surface = surface, outline = outline)
				InfoRow(label = "连接方式", value = connectModeText, surface = surface, outline = outline, valueColor = primary)
				InfoRow(label = "IP 地址", value = device?.deviceAddr ?: "-", surface = surface, outline = outline)
				InfoRow(
					label = "端口",
					value = device?.devicePort?.takeIf { it > 0 }?.toString() ?: "-",
					surface = surface,
					outline = outline,
				)
			}
		}
	}
}

@Composable
private fun SectionHeader(text: String) {
	Text(
		text = text,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(top = 20.dp, bottom = 10.dp),
		fontSize = 18.sp,
		fontWeight = FontWeight.Bold,
		color = MaterialTheme.colorScheme.onBackground
	)
}

@Composable
private fun QuickActionsGrid(
	onEnterControl: () -> Unit,
	onFileManager: () -> Unit,
	onAppManager: () -> Unit,
	onDeviceMonitor: () -> Unit,
) {
	val primary = MaterialTheme.colorScheme.primary
	val outline = MaterialTheme.colorScheme.outlineVariant
	val container = MaterialTheme.colorScheme.surface

	val items = listOf(
		QuickAction(
			title = "进入控制",
			subtitle = "远程桌面控制",
			icon = Icons.Filled.MonitorHeart,
			onClick = onEnterControl,
		),
		QuickAction(
			title = "文件管理",
			subtitle = "高速文件传输",
			icon = Icons.Filled.FolderOpen,
			onClick = onFileManager,
		),
		QuickAction(
			title = "应用管理",
			subtitle = "应用上传安装",
			icon = Icons.Filled.SystemUpdateAlt,
			onClick = onAppManager,
		),
		QuickAction(
			title = "设备监控",
			subtitle = "实时性能检测",
			icon = Icons.Filled.MonitorHeart,
			onClick = onDeviceMonitor,
		),
	)

	LazyVerticalGrid(
		columns = GridCells.Fixed(2),
		modifier = Modifier
			.fillMaxWidth()
			.height(228.dp)
			.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		userScrollEnabled = false,
	) {
		items(items) { item ->
			Card(
				modifier = Modifier
					.fillMaxWidth()
					.height(104.dp)
					.clickable(onClick = item.onClick)
					.border(1.dp, outline, RoundedCornerShape(16.dp)),
				shape = RoundedCornerShape(16.dp),
				colors = CardDefaults.cardColors(containerColor = container),
				elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
			) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(12.dp),
					verticalArrangement = Arrangement.SpaceBetween
				) {
					Icon(
						imageVector = item.icon,
						contentDescription = null,
						tint = primary,
						modifier = Modifier.size(26.dp)
					)
					Column {
						Text(
							text = item.title,
							fontWeight = FontWeight.Bold,
							fontSize = 14.sp,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							text = item.subtitle,
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 2,
							lineHeight = 13.sp,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			}
		}
	}
}

private data class QuickAction(
	val title: String,
	val subtitle: String,
	val icon: androidx.compose.ui.graphics.vector.ImageVector,
	val onClick: () -> Unit,
)


@Composable
private fun InfoRow(
	label: String,
	value: String,
	surface: Color,
	outline: Color,
	valueColor: Color = MaterialTheme.colorScheme.onSurface,
	mono: Boolean = false,
) {
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 8.dp),
		color = surface,
		shape = RoundedCornerShape(12.dp),
		tonalElevation = 0.dp,
		shadowElevation = 0.dp,
		border = androidx.compose.foundation.BorderStroke(1.dp, outline.copy(alpha = 0.7f))
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = label,
				fontSize = 13.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			Text(
				text = value,
				fontSize = 13.sp,
				fontWeight = FontWeight.Medium,
				color = valueColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null
			)
		}
	}
}

@Composable
private fun PulseDot(color: Color) {
	val transition = rememberInfiniteTransition(label = "pulseDot")
	val alpha by transition.animateFloat(
		initialValue = 0.45f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 900),
			repeatMode = RepeatMode.Reverse
		),
		label = "pulseDotAlpha"
	)
	Box(
		modifier = Modifier
			.size(10.dp)
			.background(color.copy(alpha = alpha), shape = RoundedCornerShape(999.dp))
	)
}
