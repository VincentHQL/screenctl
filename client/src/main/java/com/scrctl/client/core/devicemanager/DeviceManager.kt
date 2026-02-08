package com.scrctl.client.core.devicemanager

import kotlinx.coroutines.flow.Flow

/**
 * 设备管理器接口 —— 管理与远端设备的 ADB 连接、shell 命令执行、截屏等。
 *
 * 由 [DeviceManagerImpl] 提供实现，通过 Hilt 以 `@Singleton` 注入。
 */
interface DeviceManager {

    /** 重新连接指定设备。 */
    fun reconnect(deviceId: Long)

    /** 判断指定设备是否仍然在线。 */
    fun isConnected(deviceId: Long): Boolean

    /** 在指定设备上执行 shell 命令，返回标准输出。 */
    suspend fun shell(deviceId: Long, command: String): Result<String>

    /** 观察指定设备的连接状态。 */
    fun observeIsConnected(deviceId: Long): Flow<Boolean>

    /** 观察所有设备的连接状态映射。 */
    fun observeConnectedById(): Flow<Map<Long, Boolean>>

    /** 获取指定设备的 Monitor HTTP 客户端。 */
    fun getMonitorClient(deviceId: Long): AgentClient?

    /** 截取指定设备屏幕，返回 PNG 格式字节数组。 */
    suspend fun screencapPng(deviceId: Long): Result<ByteArray>
}
