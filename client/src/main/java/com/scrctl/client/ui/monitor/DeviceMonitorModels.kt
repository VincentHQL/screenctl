package com.scrctl.client.ui.monitor

data class DeviceMonitorUiState(
    val uiState: UiState = UiState.Loading
)

data class NetRate(
    val rxBytesPerSec: Long, 
    val txBytesPerSec: Long
)

data class BatteryInfo(
    val level: Int?,
    val status: String?,
    val temperatureC: Double?,
    val voltageMv: Int?,
)

data class StorageInfo(
    val text: String,
)

data class Metrics(
    val cpuPercent: Int?,
    val memPercent: Int?,
    val net: NetRate?,
    val battery: BatteryInfo?,
    val storage: StorageInfo?,
    val uptimeText: String?,
    val lastUpdatedAtMs: Long,
)

sealed class UiState {
    data object Loading : UiState()
    data class Error(val message: String) : UiState()
    data class Ready(val metrics: Metrics) : UiState()
}