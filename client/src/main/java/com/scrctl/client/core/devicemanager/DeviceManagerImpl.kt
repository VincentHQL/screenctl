package com.scrctl.client.core.devicemanager

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "DeviceManagerImpl"

/**
 * [DeviceManager] 的具体实现。
 *
 * 通过 [AppModule][com.scrctl.client.di.AppModule] 以 `@Provides` + `@Singleton`
 * 绑定到 [DeviceManager] 接口，由 Hilt 管理生命周期。
 */
internal class DeviceManagerImpl(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : DeviceManager {

    override fun reconnect(deviceId: Long) {
        TODO("Not yet implemented")
    }

    override fun isConnected(deviceId: Long): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun shell(deviceId: Long, command: String): Result<String> {
        TODO("Not yet implemented")
    }

    override fun observeIsConnected(deviceId: Long): Flow<Boolean> {
        TODO("Not yet implemented")
    }

    override fun observeConnectedById(): Flow<Map<Long, Boolean>> {
        TODO("Not yet implemented")
    }

    override fun getMonitorClient(deviceId: Long): AgentClient? {
        TODO("Not yet implemented")
    }

    override suspend fun screencapPng(deviceId: Long): Result<ByteArray> {
        TODO("Not yet implemented")
    }
}
