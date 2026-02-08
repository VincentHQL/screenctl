package com.scrctl.client.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group
import kotlinx.coroutines.launch

// ── Public entry point ──────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onDeviceClick: (Device) -> Unit = {},
    onAddDevice: () -> Unit = {},
) {
    HomeScreenContent(
        devices = viewModel.devices,
        allDevices = viewModel.allDevices,
        batteryByDeviceId = viewModel.batteryByDeviceId,
        isConnectedById = viewModel.isConnectedById,
        searchQuery = viewModel.searchQuery,
        gridColumns = viewModel.gridColumns,
        screencapProvider = viewModel::screencapPng,
        groups = viewModel.groups,
        selectedGroupId = viewModel.selectedGroupId,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onGridColumnsChange = viewModel::updateGridColumns,
        onDeviceClick = onDeviceClick,
        onAddDevice = onAddDevice,
        onGroupSelect = viewModel::selectGroup,
        onAddGroup = viewModel::addGroup,
        onDeleteGroup = viewModel::deleteGroup,
    )
}

// ── Content layout (stateless, testable) ────────────────────────────────────────

@Composable
private fun HomeScreenContent(
    devices: List<Device>,
    allDevices: List<Device>,
    batteryByDeviceId: Map<Long, Int?>,
    isConnectedById: Map<Long, Boolean>,
    searchQuery: String,
    gridColumns: Int,
    screencapProvider: (suspend (Long) -> Result<ByteArray>)?,
    groups: List<Group>,
    selectedGroupId: Long?,
    onSearchQueryChange: (String) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onDeviceClick: (Device) -> Unit,
    onAddDevice: () -> Unit,
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

    val onlineCount = filteredDevices.count { isConnectedById[it.id] == true }
    val standbyCount = filteredDevices.size - onlineCount

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GroupDrawerContent(
                groups = groups,
                selectedGroupId = selectedGroupId,
                allDevices = allDevices,
                onGroupSelect = { groupId ->
                    onGroupSelect(groupId)
                    scope.launch { drawerState.close() }
                },
                onCreateGroup = { name ->
                    onAddGroup(name)
                    scope.launch { drawerState.close() }
                },
                onDeleteGroup = { group ->
                    onDeleteGroup(group)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopHeader(
                    title = "设备列表",
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAddClick = onAddDevice,
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                DeviceSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                )

                SectionHeader(
                    title = "在线设备",
                    subtitle = "$onlineCount 台在线 • $standbyCount 台待机",
                    gridColumns = gridColumns,
                    onGridColumnsChange = onGridColumnsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 12.dp),
                )

                DeviceList(
                    devices = filteredDevices,
                    gridColumns = gridColumns,
                    batteryByDeviceId = batteryByDeviceId,
                    isConnectedById = isConnectedById,
                    screencapProvider = screencapProvider,
                    onDeviceClick = onDeviceClick,
                )
            }
        }
    }
}

// ── Device list / grid ──────────────────────────────────────────────────────────

@Composable
private fun DeviceList(
    devices: List<Device>,
    gridColumns: Int,
    batteryByDeviceId: Map<Long, Int?>,
    isConnectedById: Map<Long, Boolean>,
    screencapProvider: (suspend (Long) -> Result<ByteArray>)?,
    onDeviceClick: (Device) -> Unit,
) {
    if (devices.isEmpty()) {
        EmptyState(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        )
        return
    }

    val columns = gridColumns.coerceIn(1, 3)

    if (columns == 1) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(devices, key = { it.id }) { device ->
                DeviceListRow(
                    device = device,
                    batteryPercent = batteryByDeviceId[device.id],
                    isOnline = isConnectedById[device.id] == true,
                    screencapProvider = screencapProvider,
                    onClick = { onDeviceClick(device) },
                )
            }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    batteryPercent = batteryByDeviceId[device.id],
                    isOnline = isConnectedById[device.id] == true,
                    screencapProvider = screencapProvider,
                    onClick = { onDeviceClick(device) },
                )
            }
        }
    }
}

// ── Preview ─────────────────────────────────────────────────────────────────────

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
            isConnectedById = emptyMap(),
            searchQuery = "",
            gridColumns = 2,
            screencapProvider = null,
            groups = previewGroups,
            selectedGroupId = null,
            onSearchQueryChange = {},
            onGridColumnsChange = {},
            onDeviceClick = {},
            onAddDevice = {},
            onGroupSelect = {},
            onAddGroup = {},
            onDeleteGroup = {},
        )
    }
}
