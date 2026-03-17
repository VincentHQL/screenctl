package com.scrctl.client.ui.device

import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group

data class DeviceDetailUiState(
    val device: Device? = null,
    val group: Group? = null,
    val groups: List<Group> = emptyList(),
    val batteryPercent: Int? = null,
    val batteryLoading: Boolean = false,
    val isConnected: Boolean = false,
    val isSavingStreamConfig: Boolean = false,
    val deviceModel: String? = null,
    val systemVersion: String? = null,
    val deviceInfoLoading: Boolean = false,
)

enum class ConnectionMethod {
    DIRECT,
    WIRELESS,
}