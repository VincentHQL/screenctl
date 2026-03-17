package com.scrctl.client.core.scrcpy

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellResponse
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class Server(
    private val kadb: Kadb,
    private val scope: CoroutineScope,
    private val runtime: Runtime,
    private val callbacks: Callbacks = Callbacks.NONE,
) {

    data class Runtime(
        val versionName: String,
        val serverJar: File,
        val remoteJarPath: String = DEFAULT_REMOTE_JAR_PATH,
        val mainClass: String = DEFAULT_MAIN_CLASS,
    ) {
        companion object {
            const val DEFAULT_SERVER_ASSET_NAME = "scrcpy-server.jar"
            const val DEFAULT_REMOTE_JAR_PATH = "/data/local/tmp/scrcpy-server.jar"
            const val DEFAULT_MAIN_CLASS = "com.genymobile.scrcpy.Server"

            fun resolve(
                appContext: Context,
                versionName: String,
                serverAssetName: String = DEFAULT_SERVER_ASSET_NAME,
                remoteJarPath: String = DEFAULT_REMOTE_JAR_PATH,
                mainClass: String = DEFAULT_MAIN_CLASS,
            ): Runtime {
                val localJar = File(appContext.cacheDir, serverAssetName)
                appContext.assets.open(serverAssetName).use { input ->
                    localJar.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                return Runtime(
                    versionName = versionName,
                    serverJar = localJar,
                    remoteJarPath = remoteJarPath,
                    mainClass = mainClass,
                )
            }
        }
    }

    data class PortRange(
        val first: Int,
        val last: Int,
    ) {
        init {
            require(first in 1..65535) { "first must be in 1..65535" }
            require(last in 1..65535) { "last must be in 1..65535" }
            require(first <= last) { "first must be <= last" }
        }
    }

    interface Callbacks {
        fun onConnectionFailed(server: Server, error: Throwable) = Unit

        fun onConnected(server: Server, session: Session) = Unit

        fun onDisconnected(server: Server) = Unit

        companion object {
            val NONE = object : Callbacks {}
        }
    }

    data class Params(
        val scid: Int,
        val video: Boolean = true,
        val audio: Boolean = true,
        val control: Boolean = true,
        val forceAdbForward: Boolean = false,
        val portRange: PortRange = PortRange(27183, 27199),
        val videoBitRate: Int = 8000000,
        val audioBitRate: Int = 128000,
        val maxSize: Int = 0,
        val videoCodec: String = "h264",
        val audioCodec: String = "aac",
        val sendDummyByte: Boolean = true,
        val sendDeviceMeta: Boolean = true,
        val sendFrameMeta: Boolean = true,
        val sendCodecMeta: Boolean = true,
        val stayAwake: Boolean = false,
        val wmWidth: Int = 0,
        val wmHeight: Int = 0,
        val wmDensity: Int = 0,
        val connectAttempts: Int = 100,
        val connectRetryDelayMs: Long = 100L,
    )

    data class Session(
        val videoChannel: AgentChannel?,
        val audioChannel: AgentChannel?,
        val controlChannel: AgentChannel?,
        val forwarder: AutoCloseable,
        val serverJob: Job,
        val deviceName: String?,
    )

    suspend fun start(params: Params): Session {
        val remoteSocketName = buildRemoteSocketName(params.scid)
        Log.i(
            TAG,
            "Starting server bridge: scid=${String.format(Locale.US, "%08x", params.scid)}, video=${params.video}, audio=${params.audio}, control=${params.control}",
        )

        val tunnel = ServerTunnel(kadb).apply { init() }
        val tunnelOpened = tunnel.open(
            deviceSocketName = remoteSocketName,
            portRange = params.portRange,
            forceAdbForward = params.forceAdbForward,
        )
        if (!tunnelOpened) {
            val error = IOException("无法建立 ADB tunnel")
            Log.e(TAG, "Failed to open tunnel for $remoteSocketName", error)
            runCatching { callbacks.onConnectionFailed(this, error) }
            throw error
        }

        Log.i(TAG, "Tunnel opened: mode=${if (tunnel.forward) "forward" else "reverse"}, localPort=${tunnel.localPort}")

        val command = buildServerCommand(params, tunnel.forward)
        Log.d(TAG, "Built server command: $command")
        val sessionReady = AtomicBoolean(false)
        val serverTask = scope.async {
            Log.d(TAG, "Launching server process")
            kadb.shell(command)
        }
        val serverJob: Job = serverTask

        val tunnelClosed = AtomicBoolean(false)
        val tunnelHandle = AutoCloseable {
            if (tunnelClosed.compareAndSet(false, true) && tunnel.enabled) {
                Log.d(TAG, "Closing tunnel")
                tunnel.close(remoteSocketName)
            }
        }

        serverJob.invokeOnCompletion {
            if (!sessionReady.get() && tunnel.enabled) {
                Log.w(TAG, "Server process ended before session was ready, closing tunnel to unblock pending channel setup")
                runCatching { tunnelHandle.close() }
            }
        }

        var videoChannel: AgentChannel? = null
        var audioChannel: AgentChannel? = null
        var controlChannel: AgentChannel? = null

        try {
            val firstSocket = connectFirstChannelWithRetry(tunnel, params)
            Log.d(TAG, "First socket connected")

            if (params.video) {
                videoChannel = firstSocket
            }

            if (params.audio) {
                audioChannel = if (!params.video) {
                    firstSocket
                } else {
                    connectAdditionalSocket(tunnel, params, "audio")
                }
            }

            if (params.control) {
                controlChannel = if (!params.video && !params.audio) {
                    firstSocket
                } else {
                    connectAdditionalSocket(tunnel, params, "control")
                }
            }

            Log.d(TAG, "All requested channels established, closing tunnel early")
            runCatching { tunnelHandle.close() }

            val firstConnectedSocket = videoChannel ?: audioChannel ?: controlChannel
                ?: throw IOException("未建立任何 agent 通道")

            val deviceName = if (params.sendDeviceMeta) {
                consumeDeviceMeta(firstConnectedSocket)
            } else {
                null
            }

            Log.i(
                TAG,
                "Server session connected: device=${deviceName ?: "unknown"}, video=${videoChannel != null}, audio=${audioChannel != null}, control=${controlChannel != null}",
            )

            val session = Session(
                videoChannel = videoChannel,
                audioChannel = audioChannel,
                controlChannel = controlChannel,
                forwarder = tunnelHandle,
                serverJob = serverJob,
                deviceName = deviceName,
            )

            sessionReady.set(true)

            session.serverJob.invokeOnCompletion {
                Log.i(TAG, "Server process disconnected")
                runCatching { callbacks.onDisconnected(this) }
            }
            runCatching { callbacks.onConnected(this, session) }
            return session
        } catch (t: Throwable) {
            val serverDetails = awaitServerFailureDetails(serverTask)
            if (serverDetails != null) {
                Log.e(TAG, "Failed to establish server session: $serverDetails", t)
            } else {
                Log.e(TAG, "Failed to establish server session", t)
            }
            runCatching { videoChannel?.close() }
            runCatching { audioChannel?.close() }
            runCatching { controlChannel?.close() }
            runCatching { serverJob.cancel() }
            runCatching { tunnelHandle.close() }
            runCatching { callbacks.onConnectionFailed(this, t) }
            throw serverDetails?.let { IOException("$it", t) } ?: t
        }
    }

    private suspend fun awaitServerFailureDetails(serverTask: Deferred<AdbShellResponse>): String? {
        val response = withTimeoutOrNull(SERVER_FAILURE_AWAIT_MS) {
            runCatching { serverTask.await() }.getOrNull()
        } ?: return null

        val output = response.allOutput.trim()
        return buildString {
            append("server exited with code ")
            append(response.exitCode)
            if (output.isNotEmpty()) {
                append(": ")
                append(output)
            }
        }
    }

    private fun buildServerCommand(params: Params, tunnelForward: Boolean): String {
        return buildString {
            append("CLASSPATH=")
            append(runtime.remoteJarPath)
            append(" app_process / ")
            append(runtime.mainClass)
            append(" ")
            append(runtime.versionName)
            // scid 必须是 8 位 16 进制
            append(String.format(Locale.US, " scid=%08x", params.scid))
            
            if (tunnelForward) {
                append(" tunnel_forward=true")
            }
            if (!params.video) {
                append(" video=false")
            } else {
                append(" video_bit_rate=${params.videoBitRate}")
                if (params.maxSize > 0) {
                    append(" max_size=${params.maxSize}")
                }
                if (params.videoCodec != "h264") {
                    append(" video_codec=${params.videoCodec}")
                }
            }
            if (!params.audio) {
                append(" audio=false")
            } else {
                append(" audio_bit_rate=${params.audioBitRate}")
                if (params.audioCodec != "opus") {
                    append(" audio_codec=${params.audioCodec}")
                }
            }
            if (!params.control) {
                append(" control=false")
            }
            
            append(" send_dummy_byte=${params.sendDummyByte}")
            append(" send_device_meta=${params.sendDeviceMeta}")
            append(" send_frame_meta=${params.sendFrameMeta}")
            append(" send_codec_meta=${params.sendCodecMeta}")
            
            if (params.stayAwake) {
                append(" stay_awake=true")
            }
            if (params.wmWidth > 0 && params.wmHeight > 0) {
                append(" wm_size=${params.wmWidth}x${params.wmHeight}")
            }
            if (params.wmDensity > 0) {
                append(" wm_density=${params.wmDensity}")
            }
        }
    }

    private suspend fun connectFirstChannelWithRetry(
        tunnel: ServerTunnel,
        params: Params,
    ): AgentChannel {
        var lastError: Throwable? = null

        if (!tunnel.forward) {
            Log.d(TAG, "Waiting for first socket via reverse tunnel")
            return acceptSocket(tunnel)
        }

        repeat(params.connectAttempts) { attempt ->
            var channel: AgentChannel? = null
            try {
                Log.d(TAG, "Connecting first socket, attempt ${attempt + 1}/${params.connectAttempts}")
                channel = connectSocket(tunnel)
                if (params.sendDummyByte && tunnel.forward) {
                    consumeDummyByte(channel)
                    Log.d(TAG, "Dummy byte consumed")
                }
                return channel
            } catch (t: Throwable) {
                lastError = t
                runCatching { channel?.close() }
                Log.w(TAG, "First socket connect attempt ${attempt + 1} failed", t)
                if (attempt < params.connectAttempts - 1) {
                    delay(params.connectRetryDelayMs)
                }
            }
        }

        throw IOException("连接 agent 首通道失败", lastError)
    }

    private fun connectAdditionalSocket(
        tunnel: ServerTunnel,
        params: Params,
        label: String,
    ): AgentChannel {
        Log.d(TAG, "Connecting additional socket '$label' via ${if (tunnel.forward) "forward" else "reverse"} tunnel")
        val channel = if (tunnel.forward) {
            connectSocket(tunnel)
        } else {
            acceptSocket(tunnel)
        }
        Log.d(TAG, "Additional socket '$label' connected")
        return channel
    }

    private fun connectSocket(tunnel: ServerTunnel): AgentChannel {
        check(tunnel.localPort > 0) { "Tunnel local port is invalid" }
        val socket = Socket(InetAddress.getByName("127.0.0.1"), tunnel.localPort)
        return AgentChannel(socket.getInputStream(), socket.getOutputStream(), socket)
    }

    private fun acceptSocket(tunnel: ServerTunnel): AgentChannel {
        val socket = tunnel.accept()
        return AgentChannel(socket.getInputStream(), socket.getOutputStream(), socket)
    }

    private fun consumeDummyByte(channel: AgentChannel) {
        DataInputStream(channel.inputStream).readUnsignedByte()
    }

    private fun consumeDeviceMeta(channel: AgentChannel): String {
        val buffer = ByteArray(DEVICE_NAME_FIELD_LENGTH)
        DataInputStream(channel.inputStream).readFully(buffer)
        val zeroIndex = buffer.indexOf(0)
        val nameBytes = if (zeroIndex >= 0) {
            buffer.copyOfRange(0, zeroIndex)
        } else {
            buffer
        }
        return String(nameBytes, Charsets.UTF_8)
    }

    private fun buildRemoteSocketName(scid: Int): String {
        return "localabstract:scrcpy_${String.format(Locale.US, "%08x", scid)}"
    }

    companion object {
        private const val DEVICE_NAME_FIELD_LENGTH = 64
        private const val SERVER_FAILURE_AWAIT_MS = 300L
        private const val TAG = "ScrcpyServer"
    }
}

private class ServerTunnel(
    private val kadb: Kadb,
) : AutoCloseable {

    var enabled: Boolean = false
        private set

    var forward: Boolean = false
        private set

    var localPort: Int = 0
        private set

    private var serverSocket: ServerSocket? = null
    private var forwarder: AutoCloseable? = null

    fun init() {
        enabled = false
        forward = false
        localPort = 0
        serverSocket = null
        forwarder = null
    }

    fun open(deviceSocketName: String, portRange: Server.PortRange, forceAdbForward: Boolean): Boolean {
        check(!enabled) { "Tunnel is already enabled" }
        Log.d(
            TAG,
            "Opening tunnel: socket=$deviceSocketName, portRange=${portRange.first}-${portRange.last}, forceAdbForward=$forceAdbForward",
        )

        if (!forceAdbForward) {
            if (enableTunnelReverseAnyPort(deviceSocketName, portRange)) {
                Log.i(TAG, "Reverse tunnel opened on port $localPort")
                return true
            }
            Log.w(TAG, "'adb reverse' failed, fallback to 'adb forward'")
        }

        val opened = enableTunnelForwardAnyPort(deviceSocketName, portRange)
        if (opened) {
            Log.i(TAG, "Forward tunnel opened on port $localPort")
        }
        return opened
    }

    fun accept(): Socket {
        check(enabled) { "Tunnel is not enabled" }
        check(!forward) { "accept() is only valid for reverse tunnel" }
        val socket = serverSocket ?: error("serverSocket is null")
        Log.d(TAG, "Waiting for reverse tunnel connection on port $localPort")
        return socket.accept()
    }

    fun close(deviceSocketName: String): Boolean {
        check(enabled) { "Tunnel is not enabled" }
        Log.d(TAG, "Closing tunnel: socket=$deviceSocketName, mode=${if (forward) "forward" else "reverse"}, localPort=$localPort")

        val result = if (forward) {
            runCatching {
                forwarder?.close()
                true
            }.getOrElse {
                Log.w(TAG, "Could not close forward tunnel", it)
                false
            }
        } else {
            val reverseRemoved = runCatching {
                kadb.reverseKillForward(deviceSocketName)
                true
            }.getOrElse {
                Log.w(TAG, "Could not remove reverse tunnel", it)
                false
            }

            runCatching {
                serverSocket?.close()
            }.onFailure {
                Log.w(TAG, "Could not close server socket", it)
            }

            reverseRemoved
        }

        enabled = false
        forward = false
        localPort = 0
        serverSocket = null
        forwarder = null
        Log.i(TAG, "Tunnel closed")

        return result
    }

    override fun close() {
        if (!enabled) {
            return
        }
        Log.d(TAG, "Closing tunnel without remote cleanup")

        if (forward) {
            runCatching { forwarder?.close() }
        } else {
            runCatching { serverSocket?.close() }
        }

        enabled = false
        forward = false
        localPort = 0
        serverSocket = null
        forwarder = null
    }

    private fun enableTunnelReverseAnyPort(deviceSocketName: String, portRange: Server.PortRange): Boolean {
        for (port in portRange.first..portRange.last) {
            val reverseOk = runCatching {
                kadb.reverseForward(deviceSocketName, "tcp:$port")
                true
            }.getOrElse {
                return false
            }

            if (!reverseOk) {
                return false
            }

            val socket = runCatching {
                ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
            }.getOrNull()

            if (socket != null) {
                serverSocket = socket
                localPort = port
                enabled = true
                forward = false
                return true
            }

            runCatching {
                kadb.reverseKillForward(deviceSocketName)
            }.onFailure {
                Log.w(TAG, "Could not remove reverse tunnel on port $port", it)
            }

            if (port < portRange.last) {
                Log.w(TAG, "Could not listen on port $port, retrying on ${port + 1}")
            }
        }

        if (portRange.first == portRange.last) {
            Log.e(TAG, "Could not listen on port ${portRange.first}")
        } else {
            Log.e(TAG, "Could not listen on any port in range ${portRange.first}:${portRange.last}")
        }

        return false
    }

    private fun enableTunnelForwardAnyPort(deviceSocketName: String, portRange: Server.PortRange): Boolean {
        forward = true

        for (port in portRange.first..portRange.last) {
            val createdForwarder = runCatching {
                kadb.tcpForward(port, deviceSocketName)
            }.getOrNull()

            if (createdForwarder != null) {
                forwarder = createdForwarder
                localPort = port
                enabled = true
                return true
            }

            if (port < portRange.last) {
                Log.w(TAG, "Could not forward port $port, retrying on ${port + 1}")
            }
        }

        if (portRange.first == portRange.last) {
            Log.e(TAG, "Could not forward port ${portRange.first}")
        } else {
            Log.e(TAG, "Could not forward any port in range ${portRange.first}:${portRange.last}")
        }

        return false
    }

    private companion object {
        private const val TAG = "ServerTunnel"
    }
}
