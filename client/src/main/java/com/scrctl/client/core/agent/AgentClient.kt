package com.scrctl.client.core.devicemanager

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class AgentClient(
    private val socketName: String,
) : Closeable {

    private class LocalSocketFactory(private val socketName: String) : SocketFactory() {
        override fun createSocket(): Socket {
            return LocalSocketWrapper(LocalSocket(), socketName)
        }

        override fun createSocket(host: String?, port: Int): Socket = createSocket()
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket()
        override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket()
        override fun createSocket(host: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket()
    }

    private class LocalSocketWrapper(
        private val localSocket: LocalSocket,
        private val socketName: String
    ) : Socket() {
        private var _isClosed = false
        private var _pendingSoTimeout = 0
        
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            localSocket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            if (_pendingSoTimeout != 0) {
                try {
                    localSocket.soTimeout = _pendingSoTimeout
                } catch (_: IOException) {}
            }
        }

        override fun getInputStream() = localSocket.inputStream
        override fun getOutputStream() = localSocket.outputStream
        
        override fun close() {
            _isClosed = true
            localSocket.close()
        }
        
        override fun isConnected() = localSocket.isConnected
        override fun isClosed() = _isClosed

        override fun setSoTimeout(timeout: Int) {
            _pendingSoTimeout = timeout
            try {
                localSocket.soTimeout = timeout
            } catch (_: IOException) {}
        }

        override fun getSoTimeout(): Int {
            return if (localSocket.isConnected) localSocket.soTimeout else _pendingSoTimeout
        }

        override fun setTcpNoDelay(on: Boolean) {}
        override fun getTcpNoDelay() = false
        override fun setKeepAlive(on: Boolean) {}
    }

    private val baseUrl = "http://localhost"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("AgentClient_Http", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .socketFactory(LocalSocketFactory(socketName))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // ── health ──────────────────────────────────────────────────────────────

    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            httpClient.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Throwable) {
            Log.e("AgentClient", "isHealthy failed", e)
            false
        }
    }

    // ── screenshot ──────────────────────────────────────────────────────────

    suspend fun screenshot(width: Int? = null, height: Int? = null): ByteArray = withContext(Dispatchers.IO) {
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
            resp.body?.bytes() ?: throw IllegalStateException("screenshot body 为空")
        }
    }

    // ── device-info ─────────────────────────────────────────────────────────

    suspend fun deviceInfo(): MonitorDeviceInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/device-info").get().build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("device-info failed: ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("device-info body 为空")
            parseDeviceInfo(JSONObject(body))
        }
    }

    // ── packages ────────────────────────────────────────────────────────────

    suspend fun packages(
        page: Int = 0,
        pageSize: Int = 200,
        system: Boolean = true,
        user: Boolean = true,
        name: String? = null,
    ): PackageListResult = withContext(Dispatchers.IO) {
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
            parsePackageList(JSONObject(body))
        }
    }

    // ── app icon ────────────────────────────────────────────────────────────

    suspend fun appIcon(packageName: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/$packageName/icon").get().build()
        try {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
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
