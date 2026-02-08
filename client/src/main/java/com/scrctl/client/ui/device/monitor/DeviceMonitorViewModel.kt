package com.scrctl.client.ui.device.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.devicemanager.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

@HiltViewModel
class DeviceMonitorViewModel @Inject constructor(
	private val deviceManager: DeviceManager,
	private val savedStateHandle: SavedStateHandle,
	@param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

	data class NetRate(val rxBytesPerSec: Long, val txBytesPerSec: Long)

	data class BatteryInfo(
		val level: Int?,
		val status: String?,
		val temperatureC: Double?,
		val voltageMv: Int?,
	)

	data class StorageInfo(
		val text: String,
	)

	data class Metrics(
		val cpuPercent: Int?,
		val memPercent: Int?,
		val net: NetRate?,
		val battery: BatteryInfo?,
		val storage: StorageInfo?,
		val uptimeText: String?,
		val lastUpdated: Instant,
	)

	sealed class UiState {
		data object Loading : UiState()
		data class Error(val message: String) : UiState()
		data class Ready(val metrics: Metrics) : UiState()
	}

	var uiState by mutableStateOf<UiState>(UiState.Loading)
		private set

	private var deviceId: Long? = savedStateHandle.get<String>("deviceId")?.toLongOrNull()
	private var pollingJob: Job? = null

	private var lastCpu: CpuSample? = null
	private var lastNet: NetSample? = null

	fun setDeviceId(deviceId: String) {
		val parsed = deviceId.toLongOrNull() ?: return
		if (this.deviceId == parsed) return
		this.deviceId = parsed
		savedStateHandle["deviceId"] = deviceId
		stopPolling()
		uiState = UiState.Loading
		lastCpu = null
		lastNet = null
		startPolling()
	}

	fun start() {
		if (pollingJob != null) return
		startPolling()
	}

	fun retry() {
		stopPolling()
		uiState = UiState.Loading
		lastCpu = null
		lastNet = null
		startPolling()
	}

	override fun onCleared() {
		stopPolling()
		super.onCleared()
	}

	private fun startPolling() {
		val id = deviceId
		if (id == null) {
			uiState = UiState.Error("deviceId 无效")
			return
		}

		pollingJob = viewModelScope.launch {
			while (isActive) {
				val result = withContext(ioDispatcher) { sampleOnce(id) }
				result
					.onSuccess { metrics -> uiState = UiState.Ready(metrics) }
					.onFailure { e -> uiState = UiState.Error(e.message ?: "采样失败") }
				delay(1000)
			}
		}
	}

	private fun stopPolling() {
		pollingJob?.cancel()
		pollingJob = null
	}

	private suspend fun sampleOnce(deviceId: Long): Result<Metrics> = runCatching {
		val now = Instant.now()

		val cpuPercent = sampleCpu(deviceId)
		val memPercent = sampleMem(deviceId)
		val net = sampleNet(deviceId)
		val battery = sampleBattery(deviceId)
		val storage = sampleStorage(deviceId)
		val uptimeText = sampleUptime(deviceId)

		Metrics(
			cpuPercent = cpuPercent,
			memPercent = memPercent,
			net = net,
			battery = battery,
			storage = storage,
			uptimeText = uptimeText,
			lastUpdated = now,
		)
	}

	private data class CpuSample(val total: Long, val idle: Long)

	private suspend fun sampleCpu(deviceId: Long): Int? {
		val stat = deviceManager.shell(deviceId, "cat /proc/stat").getOrNull() ?: return null
		val first = stat.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
		val parts = first.split(Regex("\\s+")).filter { it.isNotBlank() }
		if (parts.size < 5) return null
		val nums = parts.drop(1).mapNotNull { it.toLongOrNull() }
		if (nums.size < 4) return null
		val user = nums.getOrNull(0) ?: return null
		val nice = nums.getOrNull(1) ?: 0L
		val system = nums.getOrNull(2) ?: 0L
		val idle = nums.getOrNull(3) ?: return null
		val iowait = nums.getOrNull(4) ?: 0L
		val irq = nums.getOrNull(5) ?: 0L
		val softirq = nums.getOrNull(6) ?: 0L
		val steal = nums.getOrNull(7) ?: 0L
		val total = user + nice + system + idle + iowait + irq + softirq + steal
		val idleAll = idle + iowait
		val current = CpuSample(total = total, idle = idleAll)
		val prev = lastCpu
		lastCpu = current
		if (prev == null) return null
		val dTotal = max(0L, current.total - prev.total)
		val dIdle = max(0L, current.idle - prev.idle)
		if (dTotal <= 0) return null
		val usage = (1.0 - (dIdle.toDouble() / dTotal.toDouble())) * 100.0
		return usage.coerceIn(0.0, 100.0).roundToInt()
	}

	private suspend fun sampleMem(deviceId: Long): Int? {
		val meminfo = deviceManager.shell(deviceId, "cat /proc/meminfo").getOrNull() ?: return null
		val totalKb = Regex("(?m)^MemTotal:\\s+(\\d+)\\s+kB").find(meminfo)?.groupValues?.getOrNull(1)?.toLongOrNull()
		val availKb = Regex("(?m)^MemAvailable:\\s+(\\d+)\\s+kB").find(meminfo)?.groupValues?.getOrNull(1)?.toLongOrNull()
		if (totalKb == null || availKb == null || totalKb <= 0) return null
		val usedPercent = (1.0 - (availKb.toDouble() / totalKb.toDouble())) * 100.0
		return usedPercent.coerceIn(0.0, 100.0).roundToInt()
	}

	private data class NetSample(val rxBytes: Long, val txBytes: Long, val atMs: Long)

	private suspend fun sampleNet(deviceId: Long): NetRate? {
		val text = deviceManager.shell(deviceId, "cat /proc/net/dev").getOrNull() ?: return null
		val lines = text.lineSequence().drop(2)
		var rx = 0L
		var tx = 0L
		for (line in lines) {
			val trimmed = line.trim()
			if (trimmed.isBlank() || !trimmed.contains(':')) continue
			val iface = trimmed.substringBefore(':').trim()
			if (iface == "lo") continue
			val cols = trimmed.substringAfter(':').trim().split(Regex("\\s+")).filter { it.isNotBlank() }
			// rx bytes is col[0], tx bytes is col[8]
			rx += cols.getOrNull(0)?.toLongOrNull() ?: 0L
			tx += cols.getOrNull(8)?.toLongOrNull() ?: 0L
		}
		val nowMs = System.currentTimeMillis()
		val current = NetSample(rxBytes = rx, txBytes = tx, atMs = nowMs)
		val prev = lastNet
		lastNet = current
		if (prev == null) return null
		val dtMs = max(1L, current.atMs - prev.atMs)
		val dRx = max(0L, current.rxBytes - prev.rxBytes)
		val dTx = max(0L, current.txBytes - prev.txBytes)
		val rxPerSec = (dRx * 1000L) / dtMs
		val txPerSec = (dTx * 1000L) / dtMs
		return NetRate(rxBytesPerSec = rxPerSec, txBytesPerSec = txPerSec)
	}

	private suspend fun sampleBattery(deviceId: Long): BatteryInfo? {
		val out = deviceManager.shell(deviceId, "dumpsys battery").getOrNull() ?: return null
		val level = Regex("(?m)^\\s*level:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toIntOrNull()
		val statusCode = Regex("(?m)^\\s*status:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toIntOrNull()
		val tempTenth = Regex("(?m)^\\s*temperature:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toIntOrNull()
		val voltage = Regex("(?m)^\\s*voltage:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toIntOrNull()
		val status = when (statusCode) {
			1 -> "未知"
			2 -> "充电中"
			3 -> "放电中"
			4 -> "未充电"
			5 -> "已充满"
			else -> null
		}
		val tempC = tempTenth?.let { it.toDouble() / 10.0 }
		return BatteryInfo(level = level, status = status, temperatureC = tempC, voltageMv = voltage)
	}

	private suspend fun sampleStorage(deviceId: Long): StorageInfo? {
		val df = deviceManager.shell(deviceId, "df -h /data").getOrNull() ?: return null
		val line = df.lineSequence()
			.map { it.trim() }
			.firstOrNull { it.isNotBlank() && !it.startsWith("Filesystem") }
		if (line == null) return null
		val cols = line.split(Regex("\\s+")).filter { it.isNotBlank() }
		val size = cols.getOrNull(1)
		val avail = cols.getOrNull(3)
		val usedPct = cols.getOrNull(4)
		val text = buildString {
			append("/data ")
			if (!avail.isNullOrBlank() && !size.isNullOrBlank()) append("剩余 $avail / $size")
			if (!usedPct.isNullOrBlank()) append(" ($usedPct)")
		}
		return StorageInfo(text = text)
	}

	private suspend fun sampleUptime(deviceId: Long): String? {
		val out = deviceManager.shell(deviceId, "cat /proc/uptime").getOrNull() ?: return null
		val first = out.trim().split(Regex("\\s+")).getOrNull(0) ?: return null
		val seconds = first.toDoubleOrNull() ?: return null
		val total = seconds.toLong()
		val h = total / 3600
		val m = (total % 3600) / 60
		val s = total % 60
		return String.format("%02d:%02d:%02d", h, m, s)
	}

	/** 代理 [DeviceManager.screencapPng]，供 Composable 使用。 */
	suspend fun screencapPng(deviceId: Long): Result<ByteArray> {
		return deviceManager.screencapPng(deviceId)
	}
}
