package com.scrctl.client.core.devicemanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.scrctl.client.BuildConfig
import com.flyfishxu.kadb.Kadb
import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.model.Device
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.Locale
import kotlin.random.Random

private const val TAG = "DeviceManagerImpl"
private const val AGENT_ASSET_NAME = "agent-server.jar"
private const val AGENT_REMOTE_PATH = "/data/local/tmp/agent-server.jar"
private const val AGENT_MAIN_CLASS = "com.scrctl.agent.Server"
private const val AGENT_STARTUP_DELAY_MS = 200L


enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

internal data class DeviceKey(val deviceAddr: String, val devicePort: Int)

internal data class DeviceConnection(
    val deviceId: Long,
    val key: DeviceKey? = null,
    val kadb: Kadb? = null,
    val forwarder: AutoCloseable? = null,
    val agentClient: AgentClient? = null,
    val serverJob: Job? = null,
    val state: DeviceConnectionState = DeviceConnectionState.CONNECTING,
)

class DeviceManagerImpl(
    private val appContext: Context,
    private val deviceDao: DeviceDao,
    private val ioDispatcher: CoroutineDispatcher,
) : DeviceManager {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val connectionsLock = Any()
    private val connections = mutableMapOf<Long, DeviceConnection>()
    private val connectedByIdFlow = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val errorByIdFlow = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val agentJarFile by lazy { extractAgentJarToCache() }
    
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        // 1. 订阅数据库设备流，自动发现新设备
        scope.launch {
            deviceDao.getAll().collectLatest { devices ->
                devices.forEach { device ->
                    val deviceId = device.id
                    val existing = getConnection(deviceId)
                    val newKey = DeviceKey(device.deviceAddr, device.devicePort)

                    if (existing == null || 
                        existing.state == DeviceConnectionState.ERROR || 
                        existing.state == DeviceConnectionState.DISCONNECTED ||
                        existing.key != newKey) {
                        connect(deviceId)
                    }
                }
            }
        }

        // 2. 监听网络变化实现自动重连
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 网络恢复时，尝试重连所有非正常连接的设备
                scope.launch {
                    val currentDevices = synchronized(connectionsLock) { connections.keys.toList() }
                    currentDevices.forEach { deviceId ->
                        val conn = getConnection(deviceId)
                        if (conn?.state != DeviceConnectionState.CONNECTED) {
                            connect(deviceId)
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                // 网络丢失时，立即更新所有连接状态为错误，刷新 UI
                synchronized(connectionsLock) {
                    connections.keys.forEach { deviceId ->
                        markConnected(deviceId, false)
                        markError(deviceId, "网络已断开")
                        updateConnection(deviceId) { it.copy(state = DeviceConnectionState.ERROR) }
                    }
                }
            }
        })
    }

    override fun observeErrorById(): StateFlow<Map<Long, String>> = errorByIdFlow.asStateFlow()

    override fun getLastError(deviceId: Long): String? = errorByIdFlow.value[deviceId]

    override fun connect(deviceId: Long) {
        scope.launch {
            connectInternal(deviceId)
        }
    }

    override suspend fun isConnected(deviceId: Long): Boolean {
        return connectedByIdFlow.value[deviceId] == true
    }

    override fun observeIsConnected(deviceId: Long): Flow<Boolean> {
        return connectedByIdFlow.map { snapshot -> snapshot[deviceId] == true }
    }

    override fun observeConnectedById(): Flow<Map<Long, Boolean>> {
        return connectedByIdFlow.asStateFlow()
    }

    override fun getAdbClient(deviceId: Long): Kadb? {
        val current = getConnection(deviceId)
        if (current?.kadb != null && current.state == DeviceConnectionState.CONNECTED) {
            return current.kadb
        }
        if (current?.state != DeviceConnectionState.CONNECTING) {
            connect(deviceId)
        }
        return null
    }

    override fun getAgentClient(deviceId: Long): AgentClient? {
        val current = getConnection(deviceId)
        if (current?.agentClient != null && current.state == DeviceConnectionState.CONNECTED) {
            return current.agentClient
        }
        if (current?.state != DeviceConnectionState.CONNECTING) {
            connect(deviceId)
        }
        return null
    }

    private fun generateAgentId(): Int = Random.nextInt() and Int.MAX_VALUE

    private suspend fun tryConnectDevice(device: Device) {
        val deviceId = device.id
        val key = DeviceKey(device.deviceAddr, device.devicePort)
        val existing = getConnection(deviceId)

        if (existing != null && existing.state == DeviceConnectionState.CONNECTED && existing.key == key) return
        if (existing != null && existing.state == DeviceConnectionState.CONNECTING && existing.key == key) return

        cleanupConnection(existing)

        putConnection(deviceId, DeviceConnection(
            deviceId = deviceId,
            key = key,
            state = DeviceConnectionState.CONNECTING,
        ))
        markConnected(deviceId, false)
        markError(deviceId, null)

        try {
            val kadb = Kadb.create(device.deviceAddr, device.devicePort)

            kadb.shell("echo connected")
            pushAgent(kadb)

            val agentId = generateAgentId()
            val remoteSocketName = buildRemoteSocketName(agentId)
            val serverJob = tryStartAgent(kadb, agentId)
            
            delay(AGENT_STARTUP_DELAY_MS)

            val localSocketName = "scrctl_agent_${agentId}"
            val forwarder = kadb.localAbstractForward(localSocketName, remoteSocketName)
            val agentClient = AgentClient(localSocketName)

            if (!agentClient.isHealthy()) {
                throw IllegalStateException("Agent 健康检查失败")
            }

            updateConnection(deviceId) {
                it.copy(
                    key = key,
                    kadb = kadb,
                    forwarder = forwarder,
                    agentClient = agentClient,
                    serverJob = serverJob,
                    state = DeviceConnectionState.CONNECTED,
                )
            }
            markConnected(deviceId, true)
            markError(deviceId, null)
        } catch (e: Throwable) {
            // 这里捕获 Broken pipe, SocketException 等，防止崩溃并通知 UI
            cleanupConnection(getConnection(deviceId))
            updateConnection(deviceId) {
                it.copy(state = DeviceConnectionState.ERROR)
            }
            markConnected(deviceId, false)
            markError(deviceId, e.message ?: e::class.java.simpleName)
        }
    }

    fun tryStartAgent(kadb: Kadb, agentId: Int): Job {
        return scope.launch {
            val command = buildString {
                append("CLASSPATH=")
                append(AGENT_REMOTE_PATH)
                append(" app_process / ")
                append(AGENT_MAIN_CLASS)
                append(" ")
                append(BuildConfig.VERSION_NAME)
                append(" aid=")
                append(String.format(Locale.US, "%08x", agentId))
            }
            kadb.shell(command)
        }
    }

    private fun buildRemoteSocketName(agentId: Int): String {
        return "localabstract:agent_${String.format(Locale.US, "%08x", agentId)}"
    }

    private suspend fun connectInternal(deviceId: Long) {
        val device = deviceDao.getById(deviceId) ?: run {
            markConnected(deviceId, false)
            markError(deviceId, "设备不存在")
            return
        }
        tryConnectDevice(device)
    }

    private fun pushAgent(kadb: Kadb) {
        kadb.push(agentJarFile, AGENT_REMOTE_PATH)
    }

    private fun extractAgentJarToCache(): File {
        val dst = File(appContext.cacheDir, AGENT_ASSET_NAME)
        appContext.assets.open(AGENT_ASSET_NAME).use { input ->
            dst.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return dst
    }

    private fun markConnected(deviceId: Long, connected: Boolean) {
        connectedByIdFlow.value = connectedByIdFlow.value + (deviceId to connected)
    }

    private fun markError(deviceId: Long, message: String?) {
        val snapshot = errorByIdFlow.value
        if (message.isNullOrBlank()) {
            errorByIdFlow.value = snapshot - deviceId
        } else {
            errorByIdFlow.value = snapshot + (deviceId to message)
        }
    }

    private fun cleanupConnection(connection: DeviceConnection?) {
        if (connection == null) return
        connection.serverJob?.cancel()
        runCatching { connection.agentClient?.close() }
        runCatching { connection.forwarder?.close() }
        runCatching { connection.kadb?.close() }
    }

    private fun getConnection(deviceId: Long): DeviceConnection? {
        return synchronized(connectionsLock) { connections[deviceId] }
    }

    private fun putConnection(deviceId: Long, connection: DeviceConnection) {
        synchronized(connectionsLock) { connections[deviceId] = connection }
    }

    private fun updateConnection(deviceId: Long, transform: (DeviceConnection) -> DeviceConnection) {
        synchronized(connectionsLock) {
            val current = connections[deviceId] ?: return
            connections[deviceId] = transform(current)
        }
    }
}
