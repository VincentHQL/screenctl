package com.scrctl.desktop.model

import androidx.compose.runtime.Immutable

@Immutable
enum class NavSection(val title: String) {
	Devices("设备管理"),
	Tasks("任务管理"),
	Scripts("脚本中心"),
	Groups("设备分组"),
	Logs("操作日志"),
	Settings("系统设置"),
}

@Immutable
enum class DeviceStatus(val label: String) {
	Online("在线"),
	Offline("离线"),
}

@Immutable
data class Device(
	val id: String,
	val name: String,
	val status: DeviceStatus,
	val batteryPercent: Int?,
	val currentApp: String?,
)

@Immutable
data class DeviceGroup(
	val id: String,
	val name: String,
	val onlineCount: Int,
	val totalCount: Int,
)

@Immutable
enum class DeviceDensityMode {
	Compact,
	Normal,
	Comfort,
}

@Immutable
data class SystemStats(
	val cpuPercent: Int,
	val uploadMbps: Double,
	val downloadMbps: Double,
	val version: String,
	val healthLabel: String,
)

@Immutable
data class BatchProgress(
	val title: String,
	val subtitle: String,
	val percent: Int,
)
