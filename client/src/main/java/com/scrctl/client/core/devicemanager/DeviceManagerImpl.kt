package com.scrctl.client.core.devicemanager

import com.flyfishxu.kadb.Kadb
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.repository.DeviceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManagerImpl @Inject constructor(
    private val deviceRepository: DeviceRepository,
    @Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : DeviceManager {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val mutex = Mutex()

    private var watchJob: Job? = null

    private data class DeviceKey(
        val connectMode: Int,
        val deviceAddr: String,
        val devicePort: Int,
    )

    private data class ManagedConnection(
        val key: DeviceKey,
        val kadb: Kadb?,
        val monitorJob: Job?,
        val state: DeviceConnectionState,
    )

    private val connections = mutableMapOf<Long, ManagedConnection>()
    private val lastDeviceSnapshot = mutableMapOf<Long, Device>()

    override fun start() {
        if (watchJob != null) return

        watchJob = scope.launch {
            deviceRepository.getAllDevices().collectLatest { devices ->
                reconcile(devices)
            }
        }
    }

    override fun stop() {
        val job = watchJob
        watchJob = null

        job?.cancel()
        scope.launch {
            mutex.withLock {
                connections.values.forEach { managed ->
                    managed.monitorJob?.cancel()
                    (managed.kadb as? Closeable)?.closeQuietly()
                }
                connections.clear()
                lastDeviceSnapshot.clear()
            }
        }
    }

    override fun reconnect(deviceId: Long) {
        scope.launch {
            val device = deviceRepository.getDeviceById(deviceId) ?: return@launch
            disconnectInternal(deviceId, markDb = true)
            connectInternal(device)
        }
    }

    override fun disconnect(deviceId: Long) {
        scope.launch { disconnectInternal(deviceId, markDb = true) }
    }

    override suspend fun shell(deviceId: Long, command: String): Result<String> {
        return runCatching {
            val kadb = mutex.withLock {
                connections[deviceId]?.kadb
            } ?: throw IllegalStateException("设备未连接")

            val resp = kadb.shell(command)
            if (resp.exitCode != 0) {
                throw IllegalStateException("shell failed: exitCode=${resp.exitCode}")
            }

            // Kadb shell result type may vary by version; try common getter names via Java reflection.
            val cls = resp.javaClass
            val candidateGetters = listOf("getOutput", "getStdout", "getOut")
            val output = candidateGetters
                .firstNotNullOfOrNull { name ->
                    try {
                        cls.getMethod(name).invoke(resp)
                    } catch (_: Throwable) {
                        null
                    }
                }

            if (output != null) return@runCatching output.toString()

            val candidateFields = listOf("output", "stdout", "out")
            val fieldValue = candidateFields.firstNotNullOfOrNull { fieldName ->
                try {
                    cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(resp)
                } catch (_: Throwable) {
                    null
                }
            }
            fieldValue?.toString() ?: resp.toString()
        }
    }

    private suspend fun reconcile(devices: List<Device>) {
        val byId = devices.associateBy { it.id }

        val toDisconnect = mutableListOf<Long>()
        val toConnect = mutableListOf<Device>()

        mutex.withLock {
            // removed devices
            val removedIds = connections.keys - byId.keys
            toDisconnect.addAll(removedIds)

            // new or changed devices
            for (device in devices) {
                val currentKey = device.toKey()
                val existing = connections[device.id]
                if (existing == null) {
                    toConnect.add(device)
                } else if (existing.key != currentKey) {
                    toDisconnect.add(device.id)
                    toConnect.add(device)
                }

                lastDeviceSnapshot[device.id] = device
            }
        }

        // Do disconnect/connect outside lock
        toDisconnect.distinct().forEach { id ->
            disconnectInternal(id, markDb = false)
        }

        toConnect.forEach { device ->
            connectInternal(device)
        }
    }

    private suspend fun connectInternal(device: Device) {
        // Avoid duplicate connects
        mutex.withLock {
            if (connections.containsKey(device.id)) return
            connections[device.id] = ManagedConnection(
                key = device.toKey(),
                kadb = null,
                monitorJob = null,
                state = DeviceConnectionState.CONNECTING,
            )
        }

        updateState(device.id, DeviceConnectionState.CONNECTING, error = "")

        try {
            val kadb = when (device.connectMode) {
                1 -> connectDirect(device)
                2 -> connectWireless(device)
                3 -> throw UnsupportedOperationException("远程 ADB Server 模式已移除")
                else -> throw IllegalArgumentException("Unknown connectMode=${device.connectMode}")
            }

            val monitor = scope.launch { monitorConnection(device.id, kadb) }

            mutex.withLock {
                connections[device.id] = ManagedConnection(
                    key = device.toKey(),
                    kadb = kadb,
                    monitorJob = monitor,
                    state = DeviceConnectionState.CONNECTED,
                )
            }

            updateState(device.id, DeviceConnectionState.CONNECTED, error = "")
        } catch (t: Throwable) {
            mutex.withLock {
                connections[device.id] = ManagedConnection(
                    key = device.toKey(),
                    kadb = null,
                    monitorJob = null,
                    state = DeviceConnectionState.ERROR,
                )
            }
            updateState(device.id, DeviceConnectionState.ERROR, error = t.message ?: t.toString())
        }
    }

    private suspend fun disconnectInternal(deviceId: Long, markDb: Boolean) {
        val toClose: ManagedConnection? = mutex.withLock {
            val existing = connections.remove(deviceId)
            lastDeviceSnapshot.remove(deviceId)
            existing
        }

        toClose?.monitorJob?.cancel()
        (toClose?.kadb as? Closeable)?.closeQuietly()

        if (markDb) {
            updateState(deviceId, DeviceConnectionState.DISCONNECTED, error = "")
        }
    }

    private suspend fun connectDirect(device: Device): Kadb {
        val host = device.deviceAddr.trim()
        val port = device.devicePort
        if (host.isEmpty()) throw IllegalArgumentException("deviceAddr 为空")
        if (port !in 1..65535) throw IllegalArgumentException("devicePort 不合法: $port")
        return Kadb.create(host, port)
    }

    private suspend fun connectWireless(device: Device): Kadb {
        val host = device.deviceAddr.trim()
        val port = device.devicePort
        if (host.isEmpty()) throw IllegalArgumentException("deviceAddr 为空")
        if (port !in 1..65535) throw IllegalArgumentException("devicePort 不合法: $port")

        val portsToTry = listOf(port, 5555).distinct()
        var lastError: Throwable? = null
        for (p in portsToTry) {
            try {
                return Kadb.create(host, p)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Unable to connect")
    }

    private suspend fun monitorConnection(deviceId: Long, kadb: Kadb) {
        while (true) {
            delay(10_000)
            try {
                val resp = kadb.shell("echo ping")
                if (resp.exitCode != 0) {
                    throw IllegalStateException("ping exitCode=${resp.exitCode}")
                }
            } catch (t: Throwable) {
                disconnectInternal(deviceId, markDb = false)
                updateState(deviceId, DeviceConnectionState.ERROR, error = t.message ?: t.toString())
                return
            }
        }
    }

    private suspend fun updateState(deviceId: Long, state: DeviceConnectionState, error: String) {
        deviceRepository.updateConnectionState(
            id = deviceId,
            state = state.name,
            error = error,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun Device.toKey(): DeviceKey = DeviceKey(
        connectMode = connectMode,
        deviceAddr = deviceAddr,
        devicePort = devicePort,
    )

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (_: Throwable) {
        }
    }
}
