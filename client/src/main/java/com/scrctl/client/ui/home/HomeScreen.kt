package com.scrctl.client.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group
import com.scrctl.client.core.devicemanager.DeviceConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onDeviceClick: (Device) -> Unit = {},
    onAddDevice: () -> Unit = {},
    onEditOperator: () -> Unit = {}
) {

    val devices = viewModel.devices
    val allDevices = viewModel.allDevices
    val batteryByDeviceId = viewModel.batteryByDeviceId
    val searchQuery = viewModel.searchQuery
    val gridColumns = viewModel.gridColumns
    val groups = viewModel.groups
    val selectedGroupId = viewModel.selectedGroupId

    HomeScreenContent(
        devices = devices,
        allDevices = allDevices,
        batteryByDeviceId = batteryByDeviceId,
        searchQuery = searchQuery,
        gridColumns = gridColumns,
        groups = groups,
        selectedGroupId = selectedGroupId,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onGridColumnsChange = viewModel::updateGridColumns,
        onDeviceClick = onDeviceClick,
        onAddDevice = onAddDevice,
        onEditOperator = onEditOperator,
        onGroupSelect = viewModel::selectGroup,
        onAddGroup = viewModel::addGroup,
        onDeleteGroup = viewModel::deleteGroup
    )
}

@Composable
private fun HomeScreenContent(
    devices: List<Device>,
    allDevices: List<Device>,
    batteryByDeviceId: Map<Long, Int?>,
    searchQuery: String,
    gridColumns: Int,
    groups: List<Group>,
    selectedGroupId: Long?,
    onSearchQueryChange: (String) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onDeviceClick: (Device) -> Unit,
    onAddDevice: () -> Unit,
    onEditOperator: () -> Unit,
    onGroupSelect: (Long?) -> Unit,
    onAddGroup: (String) -> Unit,
    onDeleteGroup: (Group) -> Unit,
) {
    val filteredDevices = remember(devices, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            devices
        } else {
            devices.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.deviceAddr.contains(q, ignoreCase = true) ||
                    it.id.toString().contains(q)
            }
        }
    }

    val onlineCount = filteredDevices.count { it.connectionState == DeviceConnectionState.CONNECTED.name }
    val standbyCount = filteredDevices.size - onlineCount

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                groups = groups,
                selectedGroupId = selectedGroupId,
                allDevices = allDevices,
                onGroupSelect = { groupId ->
                    onGroupSelect(groupId)
                    scope.launch { drawerState.close() }
                },
                onCreateGroup = { groupName ->
                    onAddGroup(groupName)
                    scope.launch { drawerState.close() }
                },
                onDeleteGroup = { group ->
                    onDeleteGroup(group)
                    scope.launch { drawerState.close() }
                },
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopHeader(
                    title = "设备列表",
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onAddClick = onAddDevice
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            )

            SectionHeader(
                title = "在线设备",
                subtitle = "$onlineCount 台在线 • $standbyCount 台待机",
                gridColumns = gridColumns,
                onGridColumnsChange = onGridColumnsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 12.dp)
            )

            when {
                filteredDevices.isEmpty() -> {
                    EmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
                else -> {
                    if (gridColumns.coerceIn(1, 3) == 1) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredDevices, key = { it.id }) { device ->
                                DeviceListRow(
                                    device = device,
                                    batteryPercent = batteryByDeviceId[device.id],
                                    onClick = { onDeviceClick(device) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            columns = GridCells.Fixed(gridColumns.coerceIn(1, 3)),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredDevices, key = { it.id }) { device ->
                                DeviceCard(
                                    device = device,
                                    batteryPercent = batteryByDeviceId[device.id],
                                    onClick = { onDeviceClick(device) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DeviceListRow(
    device: Device,
    batteryPercent: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val containerColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val error = MaterialTheme.colorScheme.error
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val connectionState = device.connectionState
    val isOnline = connectionState == DeviceConnectionState.CONNECTED.name
    val statusText = when (connectionState) {
        DeviceConnectionState.CONNECTED.name -> "在线"
        DeviceConnectionState.CONNECTING.name -> "连接中"
        DeviceConnectionState.ERROR.name -> "异常"
        else -> "离线"
    }
    val statusColor = when (connectionState) {
        DeviceConnectionState.CONNECTED.name -> primary
        DeviceConnectionState.CONNECTING.name -> secondary
        DeviceConnectionState.ERROR.name -> error
        else -> onSurfaceVariant.copy(alpha = 0.7f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, primary.copy(alpha = 0.2f), CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "IP: ${device.deviceAddr}",
                    fontSize = 10.sp,
                    color = primary.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusDot(connectionState = connectionState, modifier = Modifier.size(8.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Text(
                    text = if (batteryPercent != null) "⚡ ${batteryPercent.coerceIn(0, 100)}%" else "⚡ -",
                    fontSize = 10.sp,
                    color = onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DrawerContent(
    groups: List<Group>,
    selectedGroupId: Long?,
    allDevices: List<Device>,
    onGroupSelect: (Long?) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (Group) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(288.dp),
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
        val outlineVariant = MaterialTheme.colorScheme.outlineVariant

        var isCreatingGroup by remember { mutableStateOf(false) }
        var newGroupName by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(24.dp)
                .padding(top = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountTree,
                        contentDescription = null,
                        tint = primary,
                    )
                }
                Text(
                    text = "分组管理",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                )
            }

            // Group List
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val allCount = allDevices.size
                GroupItem(
                    icon = Icons.Filled.Devices,
                    label = "所有设备",
                    count = allCount,
                    isSelected = selectedGroupId == null,
                    onClick = { onGroupSelect(null) },
                    onDeleteClick = null
                )

                groups.forEach { group ->
                    val count = allDevices.count { it.groupId == group.id }
                    GroupItem(
                        icon = Icons.Filled.AccountTree,
                        label = group.name,
                        count = count,
                        isSelected = selectedGroupId == group.id,
                        onClick = { onGroupSelect(group.id) },
                        onDeleteClick = { onDeleteGroup(group) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create Group Button
                OutlinedButton(
                    onClick = {
                        isCreatingGroup = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = onSurfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        outlineVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "新建分组",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isCreatingGroup) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入分组名称") },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val name = newGroupName.trim()
                            if (name.isNotEmpty()) {
                                onCreateGroup(name)
                                newGroupName = ""
                                isCreatingGroup = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("确认")
                    }
                }
            }

            // Footer - User Info
            Divider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Column {
                    Text(
                        text = "管理员账号",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface.copy(alpha = 0.65f)
                    )
                    Text(
                        text = "ID: 9527-AD",
                        fontSize = 8.sp,
                        color = onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GroupItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)?
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val backgroundColor = if (isSelected) primary.copy(alpha = 0.1f) else Color.Transparent
    val textColor = if (isSelected) primary else onSurfaceVariant
    val badgeColor = if (isSelected) primary.copy(alpha = 0.2f) else surfaceVariant.copy(alpha = 0.7f)

    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onDeleteClick != null) showMenu = true
                    }
                ),
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("删除分组") },
                onClick = {
                    showMenu = false
                    onDeleteClick?.invoke()
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopHeader(
    title: String,
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val menuBg = MaterialTheme.colorScheme.surfaceVariant
    val menuIconTint = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.background,
        shape = RectangleShape,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawBehind {
                // subtle bottom divider, similar to prototype
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = dividerColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2f),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(menuBg)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "menu",
                    tint = menuIconTint
                )
            }

            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = {
            Text(
                text = "搜索设备或 ID...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    gridColumns: Int,
    onGridColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        ViewToggle(
            selectedColumns = gridColumns.coerceIn(1, 3),
            onSelect = onGridColumnsChange
        )
    }
}

@Composable
private fun ViewToggle(
    selectedColumns: Int,
    onSelect: (Int) -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ViewToggleButton(
            selected = selectedColumns == 1,
            onClick = { onSelect(1) },
            icon = Icons.Filled.ViewList,
            selectedContainerColor = selectedColor
        )
        ViewToggleButton(
            selected = selectedColumns == 2,
            onClick = { onSelect(2) },
            icon = Icons.Filled.GridView,
            selectedContainerColor = selectedColor
        )
        ViewToggleButton(
            selected = selectedColumns == 3,
            onClick = { onSelect(3) },
            icon = Icons.Filled.ViewModule,
            selectedContainerColor = selectedColor
        )
    }
}

@Composable
private fun ViewToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedContainerColor: Color,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val bg = if (selected) selectedContainerColor else Color.Transparent

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    batteryPercent: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val error = MaterialTheme.colorScheme.error

    val connectionState = device.connectionState
    val isOnline = connectionState == DeviceConnectionState.CONNECTED.name
    val statusText = when (connectionState) {
        DeviceConnectionState.CONNECTED.name -> "在线"
        DeviceConnectionState.CONNECTING.name -> "连接中"
        DeviceConnectionState.ERROR.name -> "异常"
        else -> "离线"
    }
    val statusColor = when (connectionState) {
        DeviceConnectionState.CONNECTED.name -> primary
        DeviceConnectionState.CONNECTING.name -> secondary
        DeviceConnectionState.ERROR.name -> error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                // Placeholder “screenshot” background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                )
                            )
                        )
                )

                // Light dark overlay similar to prototype
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f))
                )

                BatteryPill(
                    percent = batteryPercent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusDot(connectionState = connectionState)
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IP: ${device.deviceAddr}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun BatteryPill(
    percent: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 用文本图标兜底，避免依赖更多 Battery 图标集合
        Text(
            text = "⚡",
            color = Color.White,
            fontSize = 10.sp
        )
        Text(
            text = if (percent != null) "${percent.coerceIn(0, 100)}%" else "-",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusDot(
    connectionState: String,
    modifier: Modifier = Modifier,
) {
    val baseColor = when (connectionState) {
        DeviceConnectionState.CONNECTED.name -> MaterialTheme.colorScheme.primary
        DeviceConnectionState.CONNECTING.name -> MaterialTheme.colorScheme.secondary
        DeviceConnectionState.ERROR.name -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val shouldPulse = connectionState == DeviceConnectionState.CONNECTED.name ||
        connectionState == DeviceConnectionState.CONNECTING.name
    val transition = rememberInfiniteTransition(label = "statusDot")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusDotAlpha"
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(baseColor.copy(alpha = if (shouldPulse) pulseAlpha else 0.85f))
    )
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PowerOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无设备",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "请添加设备后重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        val previewGroups = listOf(
            Group(id = 1, name = "默认分组"),
            Group(id = 2, name = "办公测试"),
        )
        val previewDevices = listOf(
            Device(id = 1, groupId = 1, name = "智能终端 A1", deviceAddr = "192.168.1.10"),
            Device(id = 2, groupId = 1, name = "智能终端 B2", deviceAddr = "192.168.1.11"),
            Device(id = 3, groupId = 2, name = "智能终端 C3", deviceAddr = "192.168.1.12"),
            Device(id = 4, groupId = 2, name = "智能终端 D4", deviceAddr = "192.168.1.13"),
        )

        HomeScreenContent(
            devices = previewDevices,
            allDevices = previewDevices,
            batteryByDeviceId = emptyMap(),
            searchQuery = "",
            gridColumns = 2,
            groups = previewGroups,
            selectedGroupId = null,
            onSearchQueryChange = {},
            onGridColumnsChange = {},
            onDeviceClick = {},
            onAddDevice = {},
            onEditOperator = {},
            onGroupSelect = {},
            onAddGroup = {},
            onDeleteGroup = {},
        )
    }
}