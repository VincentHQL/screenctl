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
    val uiState by viewModel.uiState.collectAsState()
    
    HomeScreenContent(
        devices = uiState.devices,
        allDevices = uiState.allDevices,
        isConnectedById = uiState.isConnectedById,
        connectionErrorById = uiState.connectionErrorById,
        searchQuery = uiState.searchQuery,
        gridColumns = uiState.gridColumns,
        screencapProvider = viewModel::screencapThumbnailPng,
        groups = uiState.groups,
        selectedGroupId = uiState.selectedGroupId,
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
    isConnectedById: Map<Long, Boolean>,
    connectionErrorById: Map<Long, String>,
    searchQuery: String,
    gridColumns: Int,
    screencapProvider: (suspend (Long, Int, Int) -> Result<ByteArray>)?,
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
                    isConnectedById = isConnectedById,
                    connectionErrorById = connectionErrorById,
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
    isConnectedById: Map<Long, Boolean>,
    connectionErrorById: Map<Long, String>,
    screencapProvider: (suspend (Long, Int, Int) -> Result<ByteArray>)?,
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
                    isOnline = isConnectedById[device.id] == true,
                    errorText = connectionErrorById[device.id],
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
                    isOnline = isConnectedById[device.id] == true,
                    errorText = connectionErrorById[device.id],
                    screencapProvider = screencapProvider,
                    onClick = { onDeviceClick(device) },
                )
            }
        }
    }
}
