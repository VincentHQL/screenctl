package com.scrctl.client.core.devicemanager

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Job


enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

/**
 * 设备地址键，用于判断设备连接是否需要重建。
 */
internal data class DeviceKey(val deviceAddr: String, val devicePort: Int)

/**
 * 每个已连接设备持有的资源集合。
 *
 * - [kadb]           ADB 连接
 * - [forwarder]      local abstract socket → 本机 TCP 端口转发器
 * - [agentClient]  与设备上 scrcpy-monitor 通信的 HTTP 客户端
 * - [serverJob]      scrcpy-server 进程 shell 的协程
 * - [watchdogJob]    连接健康监测协程
 */
internal data class DeviceConnection(
    val key: DeviceKey,
    val kadb: Kadb? = null,
    val forwarder: LocalSocketForwarder? = null,
    val agentClient: AgentClient? = null,
    val serverJob: Job? = null,
    val watchdogJob: Job? = null,
    val state: DeviceConnectionState = DeviceConnectionState.CONNECTING,
)

