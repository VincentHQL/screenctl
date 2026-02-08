package com.scrctl.client.core.devicemanager

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.delay
import okio.source as okioSource

private const val TAG = "ScrcpyServerMgr"

/** scrcpy-server 在设备上的远程路径。 */
internal const val SERVER_REMOTE_PATH = "/data/local/tmp/scrcpy-server.apk"

/** 设备上 scrcpy-server 监听的 local abstract socket 名称。 */
internal const val MONITOR_SOCKET_NAME = "scrcpy_monitor"

/** 启动 scrcpy-server 的 shell 命令。 */
private const val SERVER_START_CMD =
    "CLASSPATH=$SERVER_REMOTE_PATH app_process / com.genymobile.scrcpy.Server 3.3.3 monitor=true"

/**
 * 负责 scrcpy-server 在设备上的推送、启动和健康检查。
 *
 * 不持有状态，所有方法都是纯操作；由 [DeviceManagerImpl] 组合调用。
 */
internal class ScrcpyServerManager(
    private val appContext: Context,
) {

    /**
     * 将 assets 中的 scrcpy-server.apk 推送到设备。
     */
    fun pushServer(kadb: Kadb) {
        val assetName = "scrcpy-server.apk"
        kadb.shell("mkdir -p /data/local/tmp")
        appContext.assets.open(assetName).use { input ->
            kadb.push(
                input.okioSource(),
                SERVER_REMOTE_PATH,
                420, /* 0644 */
                System.currentTimeMillis(),
            )
        }
        kadb.shell("chmod 644 \"$SERVER_REMOTE_PATH\"")
        Log.d(TAG, "scrcpy-server 推送完成")
    }

    /**
     * 阻塞式执行 scrcpy-server 进程。
     *
     * 当 shell 断开或进程退出时此方法返回。应在独立协程中运行。
     */
    fun runBlocking(kadb: Kadb) {
        try {
            kadb.openShell(SERVER_START_CMD).readAll()
        } catch (_: Throwable) {
            // server 进程退出或 ADB 断开
        }
    }

    /**
     * 等待 monitor 服务健康检查通过。
     *
     * @throws IllegalStateException 超时未就绪
     */
    suspend fun awaitReady(client: AgentClient, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (client.isHealthy()) return
            delay(500)
        }
        throw IllegalStateException("monitor 服务在 ${timeoutMs}ms 内未就绪")
    }
}
