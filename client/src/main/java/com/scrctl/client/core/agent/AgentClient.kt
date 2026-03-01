package com.scrctl.client.core.devicemanager

import android.net.LocalSocket
import android.net.LocalSocketAddress
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.Closeable
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class AgentClient(
    socketName: String,
) : Closeable {

    private class LocalSocketFactory(private val socketName: String) : SocketFactory() {

        override fun createSocket(): Socket {
            val ls = LocalSocket()
            ls.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            return ls.fileDescriptor.let { fd ->
                // 将 LocalSocket 的 FileDescriptor 包装为标准 Socket
                // 以便 OkHttp 使用
                LocalSocketWrapper(ls)
            }
        }

        override fun createSocket(host: String?, port: Int): Socket = createSocket()
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket()
        override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket()
        override fun createSocket(host: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket()
    }

    /**
     * 将 [LocalSocket] 包装为 [Socket]，使 OkHttp 能够使用其流。
     */
    private class LocalSocketWrapper(private val localSocket: LocalSocket) : Socket() {
        override fun getInputStream() = localSocket.inputStream
        override fun getOutputStream() = localSocket.outputStream
        override fun close() = localSocket.close()
        override fun isClosed() = !localSocket.isConnected
        override fun isConnected() = localSocket.isConnected
    }

    // URL 中的 host:port 不实际使用，只是满足 HTTP 协议格式要求
    private val baseUrl = "http://localhost"

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(LocalSocketFactory(socketName))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // ── health ──────────────────────────────────────────────────────────────

    fun isHealthy(): Boolean {
        return try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            httpClient.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Throwable) {
            false
        }
    }

    // ── screenshot ──────────────────────────────────────────────────────────

    fun screenshot(width: Int? = null, height: Int? = null): ByteArray {
        val url = buildString {
            append("$baseUrl/screenshot")
            val params = mutableListOf<String>()
            if (width != null) params += "width=$width"
            if (height != null) params += "height=$height"
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("screenshot failed: ${resp.code}")
            }
            return resp.body?.bytes() ?: throw IllegalStateException("screenshot body 为空")
        }
    }

    // ── device-info ─────────────────────────────────────────────────────────

    fun deviceInfo(): MonitorDeviceInfo {
        val request = Request.Builder().url("$baseUrl/device-info").get().build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("device-info failed: ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("device-info body 为空")
            return parseDeviceInfo(JSONObject(body))
        }
    }

    // ── packages ────────────────────────────────────────────────────────────

    fun packages(
        page: Int = 0,
        pageSize: Int = 200,
        system: Boolean = true,
        user: Boolean = true,
        name: String? = null,
    ): PackageListResult {
        val url = buildString {
            append("$baseUrl/packages?page=$page&pageSize=$pageSize")
            append("&system=$system&user=$user")
            if (!name.isNullOrBlank()) append("&name=$name")
        }
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("packages failed: ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("packages body 为空")
            return parsePackageList(JSONObject(body))
        }
    }

    // ── app icon ────────────────────────────────────────────────────────────

    fun appIcon(packageName: String): ByteArray? {
        val request = Request.Builder().url("$baseUrl/$packageName/icon").get().build()
        return try {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    // ── JSON parsing helpers ────────────────────────────────────────────────

    private fun parseDeviceInfo(root: JSONObject): MonitorDeviceInfo {
        val device = root.optJSONObject("device")
        val android = root.optJSONObject("android")
        val cpu = root.optJSONObject("cpu")
        val memory = root.optJSONObject("memory")
        val display = root.optJSONObject("display")
        val battery = root.optJSONObject("battery")
        val storage = root.optJSONObject("storage")

        return MonitorDeviceInfo(
            manufacturer = device?.optString("manufacturer").orEmpty(),
            brand = device?.optString("brand").orEmpty(),
            model = device?.optString("model").orEmpty(),
            serial = device?.optString("serial").orEmpty(),
            androidVersion = android?.optString("version").orEmpty(),
            sdkInt = android?.optInt("sdkInt", 0) ?: 0,
            cpuAbi = cpu?.optString("abi").orEmpty(),
            cpuCores = cpu?.optInt("cores", 0) ?: 0,
            displayWidth = display?.optInt("width", 0) ?: 0,
            displayHeight = display?.optInt("height", 0) ?: 0,
            memTotalBytes = memory?.optLong("totalBytes", 0L) ?: 0L,
            memAvailableBytes = memory?.optLong("availableBytes", 0L) ?: 0L,
            memUsagePercent = memory?.optDouble("usagePercent", 0.0) ?: 0.0,
            batteryLevel = battery?.optInt("level", -1) ?: -1,
            batteryStatus = battery?.optString("status").orEmpty(),
            batteryTemperature = battery?.optDouble("temperature", 0.0) ?: 0.0,
            storageInternal = storage?.optJSONObject("internal")?.let { parseStorageBlock(it) },
        )
    }

    private fun parseStorageBlock(obj: JSONObject): StorageBlock {
        return StorageBlock(
            total = obj.optString("total").orEmpty(),
            available = obj.optString("available").orEmpty(),
            used = obj.optString("used").orEmpty(),
            usagePercent = obj.optDouble("usagePercent", 0.0),
        )
    }

    private fun parsePackageList(root: JSONObject): PackageListResult {
        val arr = root.optJSONArray("packages") ?: return PackageListResult(emptyList(), 0)
        val list = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PackageItem(
                packageName = obj.optString("packageName").orEmpty(),
                name = obj.optString("name").orEmpty(),
                versionName = obj.optString("versionName").orEmpty(),
                versionCode = obj.optInt("versionCode", 0),
                isSystemApp = obj.optBoolean("isSystemApp", false),
                enabled = obj.optBoolean("enabled", true),
            )
        }
        return PackageListResult(
            packages = list,
            total = root.optInt("total", list.size),
        )
    }
}

// ── Data models ─────────────────────────────────────────────────────────────────

data class MonitorDeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val serial: String,
    val androidVersion: String,
    val sdkInt: Int,
    val cpuAbi: String,
    val cpuCores: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val memTotalBytes: Long,
    val memAvailableBytes: Long,
    val memUsagePercent: Double,
    val batteryLevel: Int,
    val batteryStatus: String,
    val batteryTemperature: Double,
    val storageInternal: StorageBlock?,
)

data class StorageBlock(
    val total: String,
    val available: String,
    val used: String,
    val usagePercent: Double,
)

data class PackageItem(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val isSystemApp: Boolean,
    val enabled: Boolean,
)

data class PackageListResult(
    val packages: List<PackageItem>,
    val total: Int,
)
