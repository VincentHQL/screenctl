package com.scrctl.client.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.ui.components.StatusDot
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
	onTerminal: () -> Unit,
	viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
	LaunchedEffect(deviceId) {
		viewModel.load(deviceId)
	}

	val uiState by viewModel.uiState.collectAsState()
	val device = uiState.device
	val group = uiState.group
	val batteryPercent = uiState.batteryPercent
	val batteryLoading = uiState.batteryLoading
	val isOnline = uiState.isConnected
	val deviceModel = uiState.deviceModel
	val systemVersion = uiState.systemVersion
	val deviceInfoLoading = uiState.deviceInfoLoading
	val isSavingStreamConfig = uiState.isSavingStreamConfig

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
	val connectModeText = device?.let { "${it.deviceAddr}:${it.devicePort}" } ?: "-"

	val onlineText = if (isOnline) "在线" else "离线"
	val batteryText = when {
		batteryLoading -> "电池 获取中"
		batteryPercent != null -> "电池 ${batteryPercent}%"
		else -> "电池 -"
	}
	val statusColor = if (isOnline) primary else onSurfaceVariant

	val groupName = group?.name ?: "-"
	val modelText = when {
		deviceInfoLoading && deviceModel == null -> "获取中"
		!deviceModel.isNullOrBlank() -> deviceModel
		else -> "-"
	}
	val systemVersionText = when {
		deviceInfoLoading && systemVersion == null -> "获取中"
		!systemVersion.isNullOrBlank() -> systemVersion
		else -> "-"
	}
	val heroMetaText = listOf(modelText, systemVersionText)
		.filter { it.isNotBlank() && it != "-" }
		.joinToString(" · ")
		.ifBlank { "设备 ID：$deviceId" }

	var streamConfigExpanded by rememberSaveable(device?.id) { mutableStateOf(false) }
	var videoEnabled by rememberSaveable(device?.id, device?.streamVideoEnabled) {
		mutableStateOf(device?.streamVideoEnabled ?: true)
	}
	var audioEnabled by rememberSaveable(device?.id, device?.streamAudioEnabled) {
		mutableStateOf(device?.streamAudioEnabled ?: true)
	}
	var requireAudio by rememberSaveable(device?.id, device?.streamRequireAudio) {
		mutableStateOf(device?.streamRequireAudio ?: false)
	}
	var videoBitRateText by rememberSaveable(device?.id, device?.streamVideoBitRate) {
		mutableStateOf(((device?.streamVideoBitRate ?: 8_000_000) / 1000).toString())
	}
	var audioBitRateText by rememberSaveable(device?.id, device?.streamAudioBitRate) {
		mutableStateOf(((device?.streamAudioBitRate ?: 128_000) / 1000).toString())
	}
	var maxSizeText by rememberSaveable(device?.id, device?.streamMaxSize) {
		mutableStateOf((device?.streamMaxSize ?: 0).takeIf { it > 0 }?.toString().orEmpty())
	}
	var videoCodec by rememberSaveable(device?.id, device?.streamVideoCodec) {
		mutableStateOf(device?.streamVideoCodec ?: "h264")
	}
	var audioCodec by rememberSaveable(device?.id, device?.streamAudioCodec) {
		mutableStateOf(device?.streamAudioCodec ?: "aac")
	}

	val videoBitRate = videoBitRateText.trim().toIntOrNull()?.takeIf { it > 0 }?.times(1000)
	val audioBitRate = audioBitRateText.trim().toIntOrNull()?.takeIf { it > 0 }?.times(1000)
	val maxSize = maxSizeText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
	val videoBitRateError = if (videoEnabled && videoBitRate == null) "请输入有效的视频码率" else null
	val audioBitRateError = if (audioEnabled && audioBitRate == null) "请输入有效的音频码率" else null
	val maxSizeError = when {
		maxSizeText.isBlank() -> null
		maxSize == null || maxSize < 160 -> "最长边至少为 160 像素"
		else -> null
	}
	val streamStateError = when {
		!videoEnabled && !audioEnabled -> "视频和音频不能同时关闭"
		requireAudio && !audioEnabled -> "启用强制音频时，音频流不能关闭"
		else -> null
	}
	val canSaveStreamConfig = device != null &&
		videoBitRateError == null &&
		audioBitRateError == null &&
		maxSizeError == null &&
		streamStateError == null &&
		!isSavingStreamConfig

	Scaffold(
		containerColor = background,
		snackbarHost = { SnackbarHost(snackbarHostState) },
		bottomBar = {
			Surface(
				color = background,
				shadowElevation = 16.dp,
			) {
				Button(
					onClick = onEnterControl,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 14.dp)
						.height(56.dp),
					shape = RoundedCornerShape(16.dp),
					colors = ButtonDefaults.buttonColors(
						containerColor = primary,
						contentColor = MaterialTheme.colorScheme.onPrimary,
					),
				) {
					Text(
						text = "开始远程连接",
						fontWeight = FontWeight.Bold,
						fontSize = 16.sp,
					)
				}
			}
		},
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "设备详情",
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
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
				modifier = Modifier.statusBarsPadding(),
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
							val currentDevice = device ?: run {
								showDeleteConfirm = false
								return@TextButton
							}
							isDeleting = true
							scope.launch {
								val result = viewModel.deleteDevice(currentDevice.id)
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
				},
			)
		}

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.verticalScroll(rememberScrollState())
				.padding(bottom = 20.dp),
		) {
			DeviceHeroCard(
				title = title,
				isOnline = isOnline,
				onlineText = onlineText,
				batteryText = batteryText,
				statusColor = statusColor,
				subtitle = heroMetaText,
				primary = primary,
			)

			SectionHeader(text = "核心功能")
			QuickActionsGrid(
				isOnline = isOnline,
				onFileManager = onFileManager,
				onAppManager = onAppManager,
				onDeviceMonitor = onDeviceMonitor,
				onTerminal = onTerminal,
				onUnavailable = {
					scope.launch {
						snackbarHostState.showSnackbar("设备离线，当前功能不可用")
					}
				},
			)

			SectionHeader(text = "串流参数")
			StreamConfigSection(
				surface = surface,
				outline = outline,
				primary = primary,
				expanded = streamConfigExpanded,
				onExpandedChange = { streamConfigExpanded = it },
				videoEnabled = videoEnabled,
				audioEnabled = audioEnabled,
				requireAudio = requireAudio,
				onVideoEnabledChange = { videoEnabled = it },
				onAudioEnabledChange = {
					audioEnabled = it
					if (!it) {
						requireAudio = false
					}
				},
				onRequireAudioChange = { requireAudio = it },
				videoBitRateText = videoBitRateText,
				onVideoBitRateChange = { videoBitRateText = it.filter(Char::isDigit) },
				audioBitRateText = audioBitRateText,
				onAudioBitRateChange = { audioBitRateText = it.filter(Char::isDigit) },
				maxSizeText = maxSizeText,
				onMaxSizeChange = { maxSizeText = it.filter(Char::isDigit) },
				videoCodec = videoCodec,
				onVideoCodecChange = { videoCodec = it },
				audioCodec = audioCodec,
				onAudioCodecChange = { audioCodec = it },
				videoBitRateError = videoBitRateError,
				audioBitRateError = audioBitRateError,
				maxSizeError = maxSizeError,
				streamStateError = streamStateError,
				isSaving = isSavingStreamConfig,
				canSave = canSaveStreamConfig,
				onSave = {
					if (!canSaveStreamConfig || videoBitRate == null || audioBitRate == null) return@StreamConfigSection
					scope.launch {
						viewModel.saveStreamConfig(
							videoEnabled = videoEnabled,
							audioEnabled = audioEnabled,
							requireAudio = requireAudio,
							videoBitRate = videoBitRate,
							audioBitRate = audioBitRate,
							maxSize = maxSize ?: 0,
							videoCodec = videoCodec,
							audioCodec = audioCodec,
						).onSuccess {
							snackbarHostState.showSnackbar("串流配置已保存")
						}.onFailure { error ->
							snackbarHostState.showSnackbar(error.message ?: "保存失败")
						}
					}
				},
			)

			SectionHeader(text = "设备信息")
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp),
			) {
				InfoRow(label = "型号", value = modelText, surface = surface, outline = outline)
				InfoRow(label = "系统版本", value = systemVersionText, surface = surface, outline = outline)
				InfoRow(label = "分组", value = groupName, surface = surface, outline = outline)
				InfoRow(label = "连接方式", value = connectModeText, surface = surface, outline = outline, valueColor = primary)
				InfoRow(label = "IP 地址", value = device?.deviceAddr ?: "-", surface = surface, outline = outline, mono = true)
				InfoRow(
					label = "端口",
					value = device?.devicePort?.takeIf { it > 0 }?.toString() ?: "-",
					surface = surface,
					outline = outline,
					mono = true,
				)
			}
		}
	}
}

@Composable
private fun DeviceHeroCard(
	title: String,
	isOnline: Boolean,
	onlineText: String,
	batteryText: String,
	statusColor: Color,
	subtitle: String,
	primary: Color,
) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(top = 10.dp)
			.height(232.dp)
			.background(
				brush = Brush.verticalGradient(
					colors = listOf(
						primary.copy(alpha = 0.24f),
						MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
						MaterialTheme.colorScheme.surface,
					)
				),
				shape = RoundedCornerShape(24.dp),
			)
			.border(
				width = 1.dp,
				color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
				shape = RoundedCornerShape(24.dp),
			),
	) {
		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 22.dp, end = 18.dp)
				.size(118.dp)
				.background(primary.copy(alpha = 0.08f), RoundedCornerShape(999.dp)),
		)
		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 54.dp, end = 54.dp)
				.size(44.dp)
				.background(primary.copy(alpha = 0.12f), RoundedCornerShape(999.dp)),
		)
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(22.dp),
			verticalArrangement = Arrangement.Bottom,
		) {
			Text(
				text = title,
				fontSize = 30.sp,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.onBackground,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(modifier = Modifier.height(8.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				StatusDot(isOnline = isOnline, modifier = Modifier.size(10.dp))
				Spacer(modifier = Modifier.size(8.dp))
				Text(
					text = "$onlineText | $batteryText",
					color = statusColor,
					fontSize = 13.sp,
					fontWeight = FontWeight.Medium,
				)
			}
			Spacer(modifier = Modifier.height(12.dp))
			Text(
				text = subtitle,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				fontSize = 12.sp,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun StreamConfigSection(
	surface: Color,
	outline: Color,
	primary: Color,
	expanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	videoEnabled: Boolean,
	audioEnabled: Boolean,
	requireAudio: Boolean,
	onVideoEnabledChange: (Boolean) -> Unit,
	onAudioEnabledChange: (Boolean) -> Unit,
	onRequireAudioChange: (Boolean) -> Unit,
	videoBitRateText: String,
	onVideoBitRateChange: (String) -> Unit,
	audioBitRateText: String,
	onAudioBitRateChange: (String) -> Unit,
	maxSizeText: String,
	onMaxSizeChange: (String) -> Unit,
	videoCodec: String,
	onVideoCodecChange: (String) -> Unit,
	audioCodec: String,
	onAudioCodecChange: (String) -> Unit,
	videoBitRateError: String?,
	audioBitRateError: String?,
	maxSizeError: String?,
	streamStateError: String?,
	isSaving: Boolean,
	canSave: Boolean,
	onSave: () -> Unit,
) {
	val videoCodecSummary = videoCodec.uppercase()
	val audioCodecSummary = audioCodec.uppercase()
	val videoBitRateSummary = videoBitRateText.ifBlank { "-" }
	val audioBitRateSummary = audioBitRateText.ifBlank { "-" }
	val maxSizeSummary = when (val maxSize = maxSizeText.toIntOrNull() ?: 0) {
		0 -> "分辨率保持原始"
		else -> "最长边限制 ${maxSize}px"
	}
	val expandRotation by animateFloatAsState(
		targetValue = if (expanded) 180f else 0f,
		label = "streamExpandRotation",
	)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		SummaryCard(surface = surface, outline = outline) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Box(
					modifier = Modifier
						.size(42.dp)
						.background(primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = Icons.Filled.Tune,
						contentDescription = null,
						tint = primary,
					)
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "串流参数",
						fontWeight = FontWeight.Bold,
						fontSize = 15.sp,
					)
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = if (videoEnabled) "视频流已启用" else "视频流已关闭",
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = if (audioEnabled) "音频流已启用" else "音频流已关闭",
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				IconButton(onClick = { onExpandedChange(!expanded) }) {
					Icon(
						imageVector = Icons.Filled.ExpandMore,
						contentDescription = if (expanded) "收起串流配置" else "展开串流配置",
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.graphicsLayer { rotationZ = expandRotation },
					)
				}
			}
		}

		SummaryCard(surface = surface, outline = outline) {
			Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
				ConfigMetricRow(
					icon = Icons.Filled.MovieFilter,
					primary = primary,
					label = "视频编码器",
					value = if (videoEnabled) videoCodecSummary else "已关闭",
				)
				ConfigDivider(outline = outline)
				ConfigMetricRow(
					icon = Icons.Filled.GraphicEq,
					primary = primary,
					label = "音频编码器",
					value = if (audioEnabled) audioCodecSummary else "已关闭",
				)
			}
		}

		SummaryCard(surface = surface, outline = outline) {
			Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
				ConfigMetricRow(
					icon = Icons.Filled.Memory,
					primary = primary,
					label = "码率设置",
					value = buildString {
						append("视频 ")
						append(if (videoEnabled) "${videoBitRateSummary} kbps" else "关闭")
						append(" / 音频 ")
						append(if (audioEnabled) "${audioBitRateSummary} kbps" else "关闭")
					},
				)
				ConfigDivider(outline = outline)
				ConfigMetricRow(
					icon = Icons.Filled.MonitorHeart,
					primary = primary,
					label = "分辨率设置",
					value = maxSizeSummary,
				)
			}
		}

		AnimatedVisibility(
			visible = expanded,
			enter = fadeIn() + expandVertically(),
			exit = fadeOut() + shrinkVertically(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			ConfigSectionCard(surface = surface, outline = outline, title = "基础开关") {
				SwitchRow(
					title = "启用视频流",
					subtitle = "关闭后只保留音频或控制通道",
					checked = videoEnabled,
					onCheckedChange = onVideoEnabledChange,
				)
				SwitchRow(
					title = "启用音频流",
					subtitle = "关闭后不接收设备声音",
					checked = audioEnabled,
					onCheckedChange = onAudioEnabledChange,
				)
				SwitchRow(
					title = "强制要求音频",
					subtitle = "音频不可用时直接判定连接失败",
					checked = requireAudio,
					enabled = audioEnabled,
					onCheckedChange = onRequireAudioChange,
				)
				if (streamStateError != null) {
					Text(
						text = streamStateError,
						color = MaterialTheme.colorScheme.error,
						fontSize = 12.sp,
					)
				}
			}

			ConfigSectionCard(surface = surface, outline = outline, title = "视频参数") {
				NumericField(
					value = videoBitRateText,
					onValueChange = onVideoBitRateChange,
					label = "视频码率 (kbps)",
					supportingText = "常用范围 4000 - 12000，数值越高越清晰",
					errorText = videoBitRateError,
					enabled = videoEnabled,
				)
				NumericField(
					value = maxSizeText,
					onValueChange = onMaxSizeChange,
					label = "最长边限制 (px)",
					supportingText = "留空或填 0 表示保持原始分辨率",
					errorText = maxSizeError,
				)
				CodecSelector(
					title = "视频编码",
					selected = videoCodec,
					options = listOf("h264", "h265", "av1"),
					enabled = videoEnabled,
					onSelected = onVideoCodecChange,
				)
			}

			ConfigSectionCard(surface = surface, outline = outline, title = "音频参数") {
				NumericField(
					value = audioBitRateText,
					onValueChange = onAudioBitRateChange,
					label = "音频码率 (kbps)",
					supportingText = "常用范围 96 - 256",
					errorText = audioBitRateError,
					enabled = audioEnabled,
				)
				CodecSelector(
					title = "音频编码",
					selected = audioCodec,
					options = listOf("aac", "opus", "flac"),
					enabled = audioEnabled,
					onSelected = onAudioCodecChange,
				)
			}

			Button(
				onClick = onSave,
				enabled = canSave,
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
				shape = RoundedCornerShape(14.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = primary,
					contentColor = MaterialTheme.colorScheme.onPrimary,
				),
			) {
				if (isSaving) {
			}
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.onPrimary,
					)
					Spacer(modifier = Modifier.width(8.dp))
				}
				Text("保存串流配置", fontWeight = FontWeight.Bold)
			}
		}
}

}

@Composable
private fun SummaryCard(
	modifier: Modifier = Modifier,
	surface: Color,
	outline: Color,
	content: @Composable () -> Unit,
) {
	Card(
		modifier = modifier
			.fillMaxWidth()
			.border(1.dp, outline, RoundedCornerShape(16.dp)),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = surface),
		elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			content()
		}
	}
}

@Composable
private fun ConfigSectionCard(
	surface: Color,
	outline: Color,
	title: String,
	content: @Composable () -> Unit,
) {
	SummaryCard(surface = surface, outline = outline) {
		Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
			Text(
				text = title,
				fontSize = 16.sp,
				fontWeight = FontWeight.Bold,
			)
			content()
		}
	}
}

@Composable
private fun ConfigMetricRow(
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	primary: Color,
	label: String,
	value: String,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(
			modifier = Modifier.weight(1f),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = primary,
			)
			Text(
				text = label,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
			)
		}
		Spacer(modifier = Modifier.size(12.dp))
		Text(
			text = value,
			fontSize = 12.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun ConfigDivider(outline: Color) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(1.dp)
			.background(outline.copy(alpha = 0.35f)),
	)
}

@Composable
private fun SwitchRow(
	title: String,
	subtitle: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	enabled: Boolean = true,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
			Spacer(modifier = Modifier.height(3.dp))
			Text(
				text = subtitle,
				fontSize = 12.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Spacer(modifier = Modifier.width(12.dp))
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
			enabled = enabled,
		)
	}
}

@Composable
private fun NumericField(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	supportingText: String,
	errorText: String?,
	enabled: Boolean = true,
) {
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(label) },
		enabled = enabled,
		singleLine = true,
		isError = errorText != null,
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
		supportingText = {
			Text(
				text = errorText ?: supportingText,
				color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
			)
		},
	)
}

@Composable
private fun CodecSelector(
	title: String,
	selected: String,
	options: List<String>,
	enabled: Boolean,
	onSelected: (String) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = title,
			fontSize = 14.sp,
			fontWeight = FontWeight.Medium,
		)
		Row(
			modifier = Modifier.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			options.forEach { option ->
				FilterChip(
					selected = selected == option,
					onClick = { onSelected(option) },
					enabled = enabled,
					label = { Text(option.uppercase()) },
				)
				Spacer(modifier = Modifier.width(8.dp))
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
	isOnline: Boolean,
	onFileManager: () -> Unit,
	onAppManager: () -> Unit,
	onDeviceMonitor: () -> Unit,
	onTerminal: () -> Unit,
	onUnavailable: () -> Unit,
) {
	val primary = MaterialTheme.colorScheme.primary
	val outline = MaterialTheme.colorScheme.outlineVariant
	val container = MaterialTheme.colorScheme.surface
	val unavailableTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

	val items = listOf(
		QuickAction(
			title = "文件管理",
			statusText = if (isOnline) "在线可用" else "设备离线",
			icon = Icons.Filled.FolderOpen,
			onClick = if (isOnline) onFileManager else onUnavailable,
			enabled = isOnline,
		),
		QuickAction(
			title = "应用管理",
			statusText = if (isOnline) "在线可用" else "设备离线",
			icon = Icons.Filled.SystemUpdateAlt,
			onClick = if (isOnline) onAppManager else onUnavailable,
			enabled = isOnline,
		),
		QuickAction(
			title = "设备监控",
			statusText = if (isOnline) "在线可用" else "设备离线",
			icon = Icons.Filled.MonitorHeart,
			onClick = if (isOnline) onDeviceMonitor else onUnavailable,
			enabled = isOnline,
		),
		QuickAction(
			title = "终端",
			statusText = if (isOnline) "adb shell" else "设备离线",
			icon = Icons.Filled.Code,
			onClick = if (isOnline) onTerminal else onUnavailable,
			enabled = isOnline,
		),
	)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		items.chunked(2).forEach { rowItems ->
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				rowItems.forEach { item ->
					Card(
						modifier = Modifier
							.weight(1f)
							.height(96.dp)
							.clickable(onClick = item.onClick)
							.border(1.dp, outline, RoundedCornerShape(18.dp)),
						shape = RoundedCornerShape(18.dp),
						colors = CardDefaults.cardColors(containerColor = container),
						elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
					) {
						Column(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = 8.dp, vertical = 12.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							Box(
								modifier = Modifier
									.size(36.dp)
									.background(
										color = if (item.enabled) primary.copy(alpha = 0.12f) else unavailableTint.copy(alpha = 0.12f),
										shape = RoundedCornerShape(12.dp),
									),
								contentAlignment = Alignment.Center,
							) {
								Icon(
									imageVector = item.icon,
									contentDescription = item.title,
									tint = if (item.enabled) primary else unavailableTint,
								)
							}

							Column(
								horizontalAlignment = Alignment.CenterHorizontally,
								verticalArrangement = Arrangement.spacedBy(2.dp),
							) {
								Text(
									text = item.title,
									fontWeight = FontWeight.Bold,
									fontSize = 13.sp,
									color = if (item.enabled) MaterialTheme.colorScheme.onSurface else unavailableTint,
								)
								Text(
									text = item.statusText,
									fontSize = 11.sp,
									color = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else unavailableTint,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
					}
				}

				if (rowItems.size == 1) {
					Spacer(modifier = Modifier.weight(1f))
				}
			}
		}
	}
}

private data class QuickAction(
	val title: String,
	val statusText: String,
	val icon: ImageVector,
	val onClick: () -> Unit,
	val enabled: Boolean,
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
				fontFamily = if (mono) FontFamily.Monospace else null,
			)
		}
	}
}
