package com.scrctl.client.ui.home

import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group

data class HomeUiState(
    val groups: List<Group> = emptyList(),
    val selectedGroupId: Long? = null,
    val allDevices: List<Device> = emptyList(),
    val devices: List<Device> = emptyList(),
    val searchQuery: String = "",
    val gridColumns: Int = 2,
    val isConnectedById: Map<Long, Boolean> = emptyMap(),
    val connectionErrorById: Map<Long, String> = emptyMap(),
)