package com.scrctl.client.core.devicemanager

import android.content.Context
import com.scrctl.client.BuildConfig
import com.flyfishxu.kadb.Kadb
import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.repository.DeviceRepository
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
import java.io.File
import java.util.Locale
import kotlin.random.Random

private const val TAG = "DeviceManagerImpl"
private const val AGENT_ASSET_NAME = "agent-server.jar"
private const val AGENT_REMOTE_PATH = "/data/local/tmp/agent-server.jar"
private const val AGENT_MAIN_CLASS = "com.scrctl.agent.Server"
private const val AGENT_HEALTH_RETRY_COUNT = 20
private const val AGENT_HEALTH_RETRY_DELAY_MS = 150L


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
 * - [kadb]         ADB 连接
 * - [forwarder]    设备上的 local abstract socket → 本机 abstract socket 转发器
 * - [agentClient]  与设备上 scrcpy-monitor 通信的 HTTP 客户端
 * - [serverJob]    scrcpy-server 进程 shell 的协程
 */
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


    private fun generateAgentId(): Int {
        return Random.nextInt() and Int.MAX_VALUE
    }

    private suspend fun tryConnectDevice(device: Device) {
        val deviceId = device.id
        val key = DeviceKey(device.deviceAddr, device.devicePort)
        val existing = getConnection(deviceId)

        if (existing != null && existing.state == DeviceConnectionState.CONNECTED && existing.key == key) {
            return
        }

        cleanupConnection(existing)

        putConnection(deviceId, DeviceConnection(
            deviceId = deviceId,
            key = key,
            kadb = existing?.kadb,
            forwarder = null,
            agentClient = null,
            serverJob = null,
            state = DeviceConnectionState.CONNECTING,
        ))
        markConnected(deviceId, false)
        markError(deviceId, null)

        try {
            val kadb = existing?.kadb ?: Kadb.create(device.deviceAddr, device.devicePort)

            kadb.shell("echo connected")
            pushAgent(kadb)

            updateConnection(deviceId) { it.copy(kadb = kadb) }
            val agentId = generateAgentId()
            val remoteSocketName = buildRemoteSocketName(agentId)
            val serverJob = tryStartAgent(kadb, agentId)

            val localSocketName = "scrctl_agent_${agentId}"
            val forwarder = kadb.localAbstractForward(localSocketName, remoteSocketName)
            val agentClient = AgentClient(localSocketName)

            updateConnection(deviceId) {
                it.copy(
                key = key,
                kadb = kadb,
                forwarder = forwarder,
                agentClient = agentClient,
                serverJob = serverJob,
                state = DeviceConnectionState.CONNECTING,
                )
            }

            if (!waitAgentHealthy(agentClient)) {
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
            cleanupConnection(getConnection(deviceId))
            updateConnection(deviceId) {
                it.copy(
                    forwarder = null,
                    agentClient = null,
                    serverJob = null,
                    state = DeviceConnectionState.ERROR,
                )
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

    private suspend fun waitAgentHealthy(agentClient: AgentClient): Boolean {
        repeat(AGENT_HEALTH_RETRY_COUNT) {
            if (agentClient.isHealthy()) {
                return true
            }
            delay(AGENT_HEALTH_RETRY_DELAY_MS)
        }
        return false
    }

    private fun buildRemoteSocketName(agentId: Int): String {
        return "localabstract:agent_${String.format(Locale.US, "%08x", agentId)}"
    }

    private suspend fun connectInternal(deviceId: Long) {
        val current = getConnection(deviceId)
        if (current != null && current.state == DeviceConnectionState.CONNECTED) {
            return
        }

        val device = deviceDao.getById(deviceId) ?: run {
            markConnected(deviceId, false)
            markError(deviceId, "设备不存在")
            return
        }

        tryConnectDevice(device)
    }

    private fun pushAgent(kadb: Kadb) {
        val localFile = agentJarFile
        kadb.push(localFile, AGENT_REMOTE_PATH)
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
        val snapshot = connectedByIdFlow.value
        if (snapshot[deviceId] == connected) {
            return
        }
        connectedByIdFlow.value = snapshot + (deviceId to connected)
    }

    private fun markError(deviceId: Long, message: String?) {
        val snapshot = errorByIdFlow.value
        if (message.isNullOrBlank()) {
            if (!snapshot.containsKey(deviceId)) {
                return
            }
            errorByIdFlow.value = snapshot - deviceId
            return
        }
        if (snapshot[deviceId] == message) {
            return
        }
        errorByIdFlow.value = snapshot + (deviceId to message)
    }

    private fun cleanupConnection(connection: DeviceConnection?) {
        if (connection == null) return

        connection.serverJob?.cancel()
        runCatching { connection.agentClient?.close() }
        runCatching { connection.forwarder?.close() }
    }

    private fun getConnection(deviceId: Long): DeviceConnection? {
        return synchronized(connectionsLock) {
            connections[deviceId]
        }
    }

    private fun putConnection(deviceId: Long, connection: DeviceConnection) {
        synchronized(connectionsLock) {
            connections[deviceId] = connection
        }
    }

    private fun updateConnection(deviceId: Long, transform: (DeviceConnection) -> DeviceConnection) {
        synchronized(connectionsLock) {
            val current = connections[deviceId] ?: return
            connections[deviceId] = transform(current)
        }
    }

}
