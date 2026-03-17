package com.scrctl.client.ui.app

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: AppManagerViewModel = hiltViewModel(),
    deviceId: Long,
    onBackClick: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(deviceId) {
        viewModel.setDeviceId(deviceId)
        viewModel.load()
    }

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val currentUiState = uiState.uiState
    val ready = currentUiState as? UiState.Ready
    val apps = ready?.apps.orEmpty()
    val bottomInfo = ready?.bottomInfo
    val rows = remember(apps, uiState.detailsByPackage) {
        apps.map { app ->
            val details = uiState.detailsByPackage[app.packageName]
            AppRow(
                name = viewModel.labelFor(app.packageName),
                packageName = app.packageName,
                version = details?.versionName ?: "-",
                size = if (app.isSystem) "系统" else "用户",
                isRunning = details?.isRunning ?: false,
                enabled = details?.enabled,
                isSystem = app.isSystem,
                iconUrl = null,
                supportsManage = false,
            )
        }
    }

    val filtered = remember(query, rows) {
        val q = query.trim()
        if (q.isEmpty()) return@remember rows
        rows.filter { it.name.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            Surface(
                color = background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background)
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "应用管理",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "返回",
                                    tint = onSurface,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.reload() }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "设置",
                                    tint = onSurface,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = background,
                            titleContentColor = onSurface,
                            navigationIconContentColor = onSurface,
                            actionIconContentColor = onSurface,
                        )
                    )

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "搜索已安装的应用",
                                color = onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary.copy(alpha = 0.85f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = surfaceVariant,
                            unfocusedContainerColor = surfaceVariant,
                            focusedTextColor = onSurface,
                            unfocusedTextColor = onSurface,
                            cursorColor = primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "已安装的应用",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                        )
                        Text(
                            text = "${filtered.size} 个应用",
                            fontSize = 12.sp,
                            color = onSurfaceVariant,
                        )
                    }
                }
            }
        },
        bottomBar = {
            BottomInfoBar(
                storageText = bottomInfo?.storageText ?: "剩余空间: -",
                memoryText = bottomInfo?.memoryText ?: "内存占用: -",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    ) { padding ->

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
            modifier = Modifier
                .background(background)
                .padding(padding)
        ) {
            when (currentUiState) {
                is UiState.Loading -> {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "加载中...",
                            fontSize = 12.sp,
                            color = onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                is UiState.Error -> {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = currentUiState.message,
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.reload() }
                        ) {
                            Text(
                                text = "重试",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                is UiState.Ready -> {
                    items(
                        items = filtered,
                        key = { it.packageName },
                    ) { app ->
                        AppRowItem(
                            app = app,
                            expanded = expandedPkg == app.packageName,
                            onToggleExpanded = {
                                val next = if (expandedPkg == app.packageName) null else app.packageName
                                expandedPkg = next
                                if (next != null) viewModel.ensureDetails(app.packageName)
                            },
                            onUninstall = {
                                coroutineScope.launch {
                                    viewModel.uninstall(app.packageName)
                                    viewModel.reload()
                                }
                            },
                            onClearData = {
                                coroutineScope.launch {
                                    viewModel.clearData(app.packageName)
                                    viewModel.invalidateDetails(app.packageName)
                                    viewModel.ensureDetails(app.packageName)
                                }
                            },
                            onForceStop = {
                                coroutineScope.launch {
                                    viewModel.forceStop(app.packageName)
                                    viewModel.invalidateDetails(app.packageName)
                                    viewModel.ensureDetails(app.packageName)
                                }
                            },
                            onLaunch = {
                                coroutineScope.launch {
                                    viewModel.launch(app.packageName)
                                    viewModel.invalidateDetails(app.packageName)
                                    viewModel.ensureDetails(app.packageName)
                                }
                            },
                            onToggleEnabled = {
                                val enable = app.enabled != true
                                coroutineScope.launch {
                                    viewModel.setEnabled(app.packageName, enable)
                                    viewModel.invalidateDetails(app.packageName)
                                    viewModel.ensureDetails(app.packageName)
                                }
                            },
                            onManage = {
                                coroutineScope.launch {
                                    viewModel.openAppSettings(app.packageName)
                                }
                            },
                            background = background,
                            surface = surface,
                            surfaceVariant = surfaceVariant,
                            outline = outline,
                            primary = primary,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "设备: $deviceId",
                    fontSize = 10.sp,
                    color = onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
        }
    }
}

private data class AppRow(
    val name: String,
    val packageName: String,
    val version: String,
    val size: String,
    val isRunning: Boolean,
    val enabled: Boolean?,
    val isSystem: Boolean,
    val iconUrl: String? = null,
    val supportsManage: Boolean = false,
)

@Composable
private fun AppRowItem(
    app: AppRow,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onForceStop: () -> Unit,
    onLaunch: () -> Unit,
    onToggleEnabled: () -> Unit,
    onManage: () -> Unit,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    outline: Color,
    primary: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    modifier: Modifier = Modifier,
) {
    val borderColor = outline.copy(alpha = 0.55f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(
                iconUrl = app.iconUrl,
                isRunning = app.isRunning,
                container = surfaceVariant,
                borderColor = borderColor,
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = app.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = app.size,
                        fontSize = 11.sp,
                        color = onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    fontSize = 10.sp,
                    color = onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "版本: ${app.version}",
                    fontSize = 10.sp,
                    color = onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (app.enabled == null) "状态: 未知" else if (app.enabled == true) "状态: 已启用" else "状态: 已停用",
                    fontSize = 10.sp,
                    color = onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (app.isRunning) "运行中" else "已停止",
                    fontSize = 10.sp,
                    fontWeight = if (app.isRunning) FontWeight.Medium else FontWeight.Normal,
                    color = if (app.isRunning) Color(0xFF22C55E) else onSurfaceVariant,
                )
            }

            if (app.supportsManage) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = background,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable(onClick = onManage)
                ) {
                    Text(
                        text = "管理",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded && !app.supportsManage) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppActionButton(
                        text = "卸载",
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.10f),
                        contentColor = Color(0xFFEF4444),
                        borderColor = Color(0xFFEF4444).copy(alpha = 0.20f),
                        enabled = true,
                        onClick = onUninstall,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = "清除数据",
                        containerColor = surfaceVariant,
                        contentColor = onSurface,
                        borderColor = Color.Transparent,
                        enabled = true,
                        onClick = onClearData,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = "结束运行",
                        containerColor = if (app.isRunning) Color(0xFFF97316).copy(alpha = 0.10f) else surfaceVariant,
                        contentColor = if (app.isRunning) Color(0xFFF97316) else onSurfaceVariant.copy(alpha = 0.9f),
                        borderColor = if (app.isRunning) Color(0xFFF97316).copy(alpha = 0.20f) else Color.Transparent,
                        enabled = app.isRunning,
                        onClick = onForceStop,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppActionButton(
                        text = "启动",
                        containerColor = Color(0xFF22C55E).copy(alpha = 0.10f),
                        contentColor = Color(0xFF16A34A),
                        borderColor = Color(0xFF22C55E).copy(alpha = 0.20f),
                        enabled = true,
                        onClick = onLaunch,
                        modifier = Modifier.weight(1f)
                    )

                    val canToggle = !app.isSystem && app.enabled != null
                    AppActionButton(
                        text = if (app.enabled == false) "启用" else "停用",
                        containerColor = if (canToggle) Color(0xFF3B82F6).copy(alpha = 0.10f) else surfaceVariant,
                        contentColor = if (canToggle) Color(0xFF2563EB) else onSurfaceVariant.copy(alpha = 0.9f),
                        borderColor = if (canToggle) Color(0xFF3B82F6).copy(alpha = 0.20f) else Color.Transparent,
                        enabled = canToggle,
                        onClick = onToggleEnabled,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(outline.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun AppIcon(
    iconUrl: String?,
    isRunning: Boolean,
    container: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .background(container),
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        if (isRunning) {
            RunningBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun RunningBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "runningPing")
    val pingAlpha by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "pingAlpha"
    )
    val pingScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "pingScale"
    )

    Box(modifier = modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((12.dp.value * pingScale).dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = pingAlpha))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF16A34A))
        )
    }
}

@Composable
private fun AppActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val finalContainer = if (enabled) containerColor else containerColor.copy(alpha = 0.55f)
    val finalContent = if (enabled) contentColor else contentColor.copy(alpha = 0.6f)

    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick),
        color = finalContainer,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = finalContent,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomInfoBar(
    storageText: String,
    memoryText: String,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .border(1.dp, outline.copy(alpha = 0.55f)),
        color = background.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = storageText,
                    fontSize = 12.sp,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = memoryText,
                    fontSize = 12.sp,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
