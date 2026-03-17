package com.scrctl.client.core.devicemanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellPacket
import com.scrctl.client.BuildConfig
import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.utils.PngUtils
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
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val TAG = "DeviceManagerImpl"
private const val KEEP_ALIVE_INTERVAL_MS = 5_000L
private const val KEEP_ALIVE_COMMAND = "echo keepalive"
private const val RECONNECT_INTERVAL_MS = 2_000L
private const val SERVER_PATH_REMOTE = "/data/local/tmp/scrcpy-tools.jar"
private const val SERVER_ASSET_NAME = "scrcpy-server.jar"

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
    val watchJob: Job? = null,
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
                // 网络丢失时，关闭现有连接并更新状态，刷新 UI
                val snapshot = synchronized(connectionsLock) { connections.toMap() }
                snapshot.forEach { (deviceId, conn) ->
                    cleanupConnection(conn)
                    updateConnection(deviceId) {
                        it.copy(
                            kadb = null,
                            watchJob = null,
                            state = DeviceConnectionState.ERROR,
                        )
                    }
                    markConnected(deviceId, false)
                    markError(deviceId, "网络已断开")
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

    override suspend fun screencapThumbnailPng(deviceId: Long, width: Int, height: Int): Result<ByteArray> {
        return runCatching {
            withContext(ioDispatcher) {
                val adbClient = getAdbClient(deviceId)
                    ?: throw IllegalStateException("设备未连接")
                val command = "CLASSPATH=/data/local/tmp/scrcpy-tools.jar app_process / com.genymobile.scrcpy.Server ${BuildConfig.VERSION_NAME} log_level=error screen_cap=true screen_cap_size=${width}x${height}"
                var pngResult: ByteArray? = null
                adbClient.openShell(command).use { shell ->
                    val stdout = ByteArrayOutputStream(16 * 1024)
                    val stderr = StringBuilder()

                    while (true) {
                        when (val packet = shell.read()) {
                            is AdbShellPacket.StdOut -> stdout.write(packet.payload)
                            is AdbShellPacket.StdError -> stderr.append(packet.payload.decodeToString())
                            is AdbShellPacket.Exit -> {
                                val exitCode = packet.payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
                                val raw = stdout.toByteArray()
                                val png = PngUtils.extractPngBytes(raw)
                                if (png != null) {
                                    pngResult = png
                                    break
                                }
                                val stderrText = stderr.toString().trim()
                                val stdoutPreview = raw.toUtf8Preview(180)
                                val detail = buildString {
                                    append("设备未返回有效PNG截屏数据")
                                    append(" (exit=")
                                    append(exitCode)
                                    append(")")
                                    if (stderrText.isNotEmpty()) {
                                        append("; stderr=")
                                        append(stderrText)
                                    }
                                    if (stdoutPreview.isNotEmpty()) {
                                        append("; stdout=")
                                        append(stdoutPreview)
                                    }
                                }
                                throw IOException(detail)
                            }
                        }
                    }
                }
                pngResult ?: throw IOException("设备未返回有效PNG截屏数据")
            }
        }
    }

    private fun ByteArray.toUtf8Preview(maxLen: Int): String {
        if (isEmpty() || maxLen <= 0) {
            return ""
        }
        val text = decodeToString()
            .replace('\u0000', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length <= maxLen) {
            return text
        }
        return text.take(maxLen) + "…"
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
            val kadb = withContext(ioDispatcher) {
                Kadb.create(device.deviceAddr, device.devicePort)
            }

            kadb.shell("echo connected")
            kadb.shell("rm $SERVER_PATH_REMOTE")

            // 连接成功后，立即推送 scrcpy-server.jar
            pushServerJar(kadb)

            val watchJob = scope.launch {
                while (true) {
                    delay(KEEP_ALIVE_INTERVAL_MS)
                    try {
                        kadb.shell(KEEP_ALIVE_COMMAND)
                    } catch (e: Throwable) {
                        runCatching { kadb.close() }
                        updateConnection(deviceId) {
                            it.copy(
                                kadb = null,
                                watchJob = null,
                                state = DeviceConnectionState.ERROR,
                            )
                        }
                        markConnected(deviceId, false)
                        markError(deviceId, "连接断开，正在重连")

                        while (true) {
                            delay(RECONNECT_INTERVAL_MS)
                            connectInternal(deviceId)
                            if (getConnection(deviceId)?.state == DeviceConnectionState.CONNECTED) {
                                break
                            }
                        }

                        break
                    }
                }
            }

            updateConnection(deviceId) {
                it.copy(
                    key = key,
                    kadb = kadb,
                    watchJob = watchJob,
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

    private suspend fun pushServerJar(kadb: Kadb) {
        withContext(ioDispatcher) {
            try {
                appContext.assets.open(SERVER_ASSET_NAME).use { inputStream ->
                    val source = inputStream.source().buffer()
                    // 推送到 /data/local/tmp/scrcpy-server.jar
                    kadb.push(source, SERVER_PATH_REMOTE, 0 red 7 or (5 shl 3) or (5 shl 6), System.currentTimeMillis())
                }
            } catch (e: IOException) {
                throw IOException("无法推送 scrcpy-server.jar: ${e.message}")
            }
        }
    }

    // 辅助扩展：为了简化权限设置 (0755)
    private infix fun Int.red(other: Int): Int = this or other

    private suspend fun connectInternal(deviceId: Long) {
        val device = deviceDao.getById(deviceId) ?: run {
            markConnected(deviceId, false)
            markError(deviceId, "设备不存在")
            return
        }
        tryConnectDevice(device)
    }

    private fun markConnected(deviceId: Long, connected: Boolean) {
        connectedByIdFlow.value += (deviceId to connected)
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
        connection.watchJob?.cancel()
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
