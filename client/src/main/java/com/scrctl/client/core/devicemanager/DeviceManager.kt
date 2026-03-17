package com.scrctl.client.core.devicemanager

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceManager {
    // 异步连接设备
    fun connect(deviceId: Long)

    // 获取设备缩略图截图
    suspend fun screencapThumbnailPng(deviceId: Long, width: Int, height: Int): Result<ByteArray>

    // 查询单设备当前连接状态
    suspend fun isConnected(deviceId: Long): Boolean

    // 观察单设备连接状态
    fun observeIsConnected(deviceId: Long): Flow<Boolean>

    // 观察所有设备连接快照
    fun observeConnectedById(): Flow<Map<Long, Boolean>>

    fun observeErrorById(): StateFlow<Map<Long, String>>

    fun getLastError(deviceId: Long): String?

    //获取adb连接
    fun getAdbClient(deviceId: Long): Kadb?


}
