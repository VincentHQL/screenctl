package com.scrctl.client.core.devicemanager

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.flyfishxu.kadb.Kadb
import okio.buffer
import okio.sink
import okio.source
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 将设备上的 local abstract socket 通过 ADB 协议桥接到客户端本地的 abstract socket。
 *
 * 原理：在客户端创建一个 [LocalServerSocket]，监听名为 [localSocketName] 的
 * abstract socket。每当有本地连接进来，就通过 [Kadb.open] 打开一条到
 * `localabstract:{remoteSocketName}` 的 ADB 流，然后双向转发数据。
 *
 * 使用方法：
 * ```
 * // 将设备的 scrcpy_monitor 映射到客户端的 my_monitor
 * val forwarder = LocalSocketForwarder(kadb, "scrcpy_monitor", "my_monitor")
 * forwarder.start()
 * // 其他进程可通过 LocalSocket 连接到 abstract socket "my_monitor"
 *
 * // localSocketName 省略时默认与 remoteSocketName 相同
 * val forwarder = LocalSocketForwarder(kadb, "scrcpy_monitor")
 * forwarder.start()
 * // 连接到 abstract socket "scrcpy_monitor" 即可访问设备端服务
 *
 * forwarder.close()                       // 停止转发
 * ```
 *
 * @param kadb              已连接的 Kadb 实例
 * @param remoteSocketName  设备上的 abstract socket 名称（不含 `localabstract:` 前缀）
 * @param localSocketName   客户端 abstract socket 名称；若为 null 则使用 [remoteSocketName]
 */
internal class LocalSocketForwarder(
    private val kadb: Kadb,
    private val remoteSocketName: String,
    private val localSocketName: String? = null,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private var localServerSocket: LocalServerSocket? = null
    private var acceptThread: Thread? = null
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "lsf-worker-$remoteSocketName").apply { isDaemon = true }
    }

    /** 实际使用的本地 abstract socket 名称。 */
    val resolvedLocalSocketName: String
        get() = localSocketName ?: remoteSocketName

    fun start() {
        val name = resolvedLocalSocketName
        val lss = LocalServerSocket(name)
        localServerSocket = lss

        acceptThread = thread(name = "lsf-accept-$name", isDaemon = true) {
            try {
                while (!closed.get() && !Thread.currentThread().isInterrupted) {
                    val client: LocalSocket = lss.accept()
                    executor.execute { handleBridge(client) }
                }
            } catch (_: Throwable) {
                // LocalServerSocket closed or interrupted
            }
        }
    }

    // ── 双向桥接 ────────────────────────────────────────────────────────────

    private fun handleBridge(client: LocalSocket) {
        try {
            val adbStream = kadb.open("localabstract:$remoteSocketName")

            // ADB → 客户端
            val readerThread = thread(isDaemon = true) {
                try {
                    val src = adbStream.source
                    val sink = client.outputStream.sink().buffer()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = src.read(buf)
                        if (n == -1) break
                        sink.write(buf, 0, n)
                        sink.flush()
                    }
                } catch (_: Throwable) {
                    // stream closed or error
                } finally {
                    try { client.close() } catch (_: Throwable) {}
                    try { adbStream.close() } catch (_: Throwable) {}
                }
            }

            // 客户端 → ADB
            try {
                val src = client.inputStream.source().buffer()
                val sink = adbStream.sink
                val buf = ByteArray(8192)
                while (true) {
                    val n = src.read(buf)
                    if (n == -1) break
                    sink.write(buf, 0, n)
                    sink.flush()
                }
            } catch (_: Throwable) {
                // stream closed or error
            } finally {
                try { adbStream.close() } catch (_: Throwable) {}
                try { client.close() } catch (_: Throwable) {}
                readerThread.interrupt()
            }
        } catch (_: Throwable) {
            try { client.close() } catch (_: Throwable) {}
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { localServerSocket?.close() } catch (_: Throwable) {}
        acceptThread?.interrupt()
        executor.shutdownNow()
    }
}
