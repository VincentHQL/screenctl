package com.scrctl.client.core.devicemanager

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * 设备连接健康监测器。
 *
 * 功能包括：
 * - 单设备连接 watchdog（`monitorConnection`）
 * - 全局连通性快照定期刷新（`refreshConnectivitySnapshot`）
 * - 带 TTL 缓存的 `isConnected` 查询
 */
internal class ConnectivityMonitor(
    private val mutex: Mutex,
    private val connections: MutableMap<Long, DeviceConnection>,
    private val connectedByIdFlow: MutableStateFlow<Map<Long, Boolean>>,
    private val onDisconnect: suspend (Long) -> Unit,
) {

    private data class PingCache(val atMs: Long, val ok: Boolean)

    private val pingCacheById = mutableMapOf<Long, PingCache>()

    // ── single-device check ─────────────────────────────────────────────────

    /**
     * 判断指定设备是否仍在线（带 2 秒 TTL 缓存）。
     */
    suspend fun isConnected(deviceId: Long): Boolean {
        val nowMs = System.currentTimeMillis()
        val (kadb, state, cached) = mutex.withLock {
            val m = connections[deviceId]
            Triple(m?.kadb, m?.state, pingCacheById[deviceId])
        }
        if (kadb == null || state != DeviceConnectionState.CONNECTED) return false
        if (cached != null && nowMs - cached.atMs < CACHE_TTL_MS) return cached.ok

        val ok = isKadbAlive(kadb)
        mutex.withLock { pingCacheById[deviceId] = PingCache(nowMs, ok) }
        return ok
    }

    // ── per-device watchdog ─────────────────────────────────────────────────

    /**
     * 持续监测单个设备的连接状态，发现断线时触发断连。
     *
     * 此方法会无限挂起，应在独立协程中运行。
     */
    suspend fun monitorConnection(deviceId: Long, kadb: Kadb, client: AgentClient) {
        while (coroutineContext.isActive) {
            delay(WATCHDOG_INTERVAL_MS)
            try {
                val monitorOk = runCatching { client.isHealthy() }.getOrDefault(false)
                val adbOk = isKadbAlive(kadb)
                if (!monitorOk && !adbOk) throw IllegalStateException("connection lost")
            } catch (_: Throwable) {
                onDisconnect(deviceId)
                return
            }
        }
    }

    // ── global snapshot refresh ─────────────────────────────────────────────

    /**
     * 刷新所有被追踪设备的连通性状态。由定时循环调用。
     */
    suspend fun refreshConnectivitySnapshot(trackedDeviceIds: Set<Long>) {
        if (trackedDeviceIds.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        val candidates = mutex.withLock {
            connections.mapNotNull { (id, m) ->
                if (m.kadb != null && m.state == DeviceConnectionState.CONNECTED) {
                    Triple(id, m.kadb, m.agentClient)
                } else null
            }
        }

        val next = connectedByIdFlow.value.toMutableMap().apply {
            trackedDeviceIds.forEach { putIfAbsent(it, false) }
        }

        for ((id, kadb, client) in candidates) {
            val cached = mutex.withLock {
                val c = pingCacheById[id]
                if (c != null && nowMs - c.atMs < CACHE_TTL_MS) c.ok else null
            }

            val ok = cached ?: run {
                val alive = (client != null && runCatching { client.isHealthy() }.getOrDefault(false))
                        || isKadbAlive(kadb)
                alive
            }

            mutex.withLock { pingCacheById[id] = PingCache(nowMs, ok) }
            next[id] = ok
            if (!ok) onDisconnect(id)
        }

        connectedByIdFlow.value = next
    }

    /** 清除指定设备的 ping 缓存。 */
    fun clearCache(deviceId: Long) {
        pingCacheById.remove(deviceId)
    }

    /** 清除所有缓存。 */
    fun clearAllCaches() {
        pingCacheById.clear()
    }

    // ── internal ────────────────────────────────────────────────────────────

    companion object {
        private const val CACHE_TTL_MS = 2_000L
        private const val WATCHDOG_INTERVAL_MS = 10_000L
    }
}

/** ADB 连接是否存活（轻量级 echo 命令 + 超时）。 */
internal suspend fun isKadbAlive(kadb: Kadb): Boolean {
    return try {
        withTimeout(3_000) { kadb.shell("echo 1").exitCode == 0 }
    } catch (_: Throwable) {
        false
    }
}
