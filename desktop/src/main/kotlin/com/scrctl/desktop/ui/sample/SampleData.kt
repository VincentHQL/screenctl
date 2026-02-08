package com.scrctl.desktop.ui.sample

import com.scrctl.desktop.model.BatchProgress
import com.scrctl.desktop.model.Device
import com.scrctl.desktop.model.DeviceGroup
import com.scrctl.desktop.model.DeviceStatus
import com.scrctl.desktop.model.SystemStats

object SampleData {
	val groups: List<DeviceGroup> = listOf(
		DeviceGroup(id = "all", name = "所有设备", onlineCount = 48, totalCount = 50),
		DeviceGroup(id = "ungrouped", name = "未分组", onlineCount = 2, totalCount = 2),
		DeviceGroup(id = "douyin", name = "抖音运营组", onlineCount = 20, totalCount = 22),
		DeviceGroup(id = "dm", name = "私信营销组", onlineCount = 15, totalCount = 15),
		DeviceGroup(id = "spare", name = "备用设备", onlineCount = 11, totalCount = 11),
	)

	val devices: List<Device> = listOf(
		Device(
			id = "001",
			name = "节点_001",
			status = DeviceStatus.Online,
			batteryPercent = 98,
			currentApp = "系统设置",
		),
		Device(
			id = "002",
			name = "节点_002",
			status = DeviceStatus.Online,
			batteryPercent = 64,
			currentApp = "社交应用",
		),
		Device(
			id = "003",
			name = "节点_003",
			status = DeviceStatus.Offline,
			batteryPercent = null,
			currentApp = null,
		),
		Device(id = "004", name = "节点_004", status = DeviceStatus.Online, batteryPercent = 72, currentApp = "抖音"),
		Device(id = "005", name = "节点_005", status = DeviceStatus.Online, batteryPercent = 35, currentApp = "消息"),
		Device(id = "006", name = "节点_006", status = DeviceStatus.Online, batteryPercent = 87, currentApp = "浏览器"),
	)

	val stats: SystemStats = SystemStats(
		cpuPercent = 24,
		uploadMbps = 12.4,
		downloadMbps = 1.2,
		version = "v4.8.5-stable",
		healthLabel = "系统运行正常",
	)

	val progress: BatchProgress = BatchProgress(
		title = "批量 APK 安装",
		subtitle = "正在 50 台设备上同步...",
		percent = 72,
	)
}
