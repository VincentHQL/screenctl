package com.scrctl.client.ui.device.app

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class AppManagerViewModel @Inject constructor(
	private val deviceManager: DeviceManager,
	private val savedStateHandle: SavedStateHandle,
	@param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

	data class AppItem(
		val packageName: String,
		val apkPath: String?,
		val isSystem: Boolean,
	)

	data class AppDetails(
		val versionName: String?,
		val enabled: Boolean?,
		val isRunning: Boolean?,
		val apkPaths: List<String>?,
	)

	data class BottomInfo(
		val storageText: String,
		val memoryText: String,
	)

	sealed class UiState {
		data object Loading : UiState()
		data class Error(val message: String) : UiState()
		data class Ready(
			val apps: List<AppItem>,
			val bottomInfo: BottomInfo,
		) : UiState()
	}

	var uiState by mutableStateOf<UiState>(UiState.Loading)
		private set

	var detailsByPackage by mutableStateOf<Map<String, AppDetails>>(emptyMap())
		private set

	private var deviceId: Long? = savedStateHandle.get<String>("deviceId")?.toLongOrNull()

	fun setDeviceId(deviceId: String) {
		val parsed = deviceId.toLongOrNull() ?: return
		if (this.deviceId == parsed) return
		this.deviceId = parsed
		savedStateHandle["deviceId"] = deviceId
		uiState = UiState.Loading
		detailsByPackage = emptyMap()
		reload()
	}

	fun load() {
		if (uiState !is UiState.Loading) return
		reload()
	}

	fun reload() {
		val id = deviceId
		if (id == null) {
			uiState = UiState.Error("deviceId 无效")
			return
		}

		uiState = UiState.Loading
		viewModelScope.launch {
			val result = withContext(ioDispatcher) {
				runCatching {
					val user = listPackages(id, userOnly = true)
					val system = listPackages(id, userOnly = false)
					val all = mergePackages(user, system)
					val bottom = loadBottomInfo(id)
					UiState.Ready(apps = all, bottomInfo = bottom)
				}
			}

			uiState = result.getOrElse { UiState.Error(it.message ?: "加载失败") }
		}
	}

	fun labelFor(packageName: String): String {
		// 没有额外服务时，很难直接拿到 label；用包名末段做一个可读的兜底。
		return packageName.substringAfterLast('.').ifBlank { packageName }
	}

	fun ensureDetails(packageName: String) {
		val id = deviceId ?: return
		if (detailsByPackage.containsKey(packageName)) return

		viewModelScope.launch {
			val result = withContext(ioDispatcher) {
				runCatching {
					val dumpsys = deviceManager.shell(id, "dumpsys package $packageName").getOrNull().orEmpty()
					val versionName = Regex("(?m)^\\s*versionName=(.+)$").find(dumpsys)?.groupValues?.getOrNull(1)?.trim()
					val enabled = Regex("(?m)^\\s*enabled=(true|false)\\b").find(dumpsys)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()

					val pid = deviceManager.shell(id, "pidof $packageName").getOrNull().orEmpty().trim()
					val isRunning = pid.isNotBlank()

					val apkPaths = deviceManager.shell(id, "pm path $packageName")
						.getOrNull()
						.orEmpty()
						.lineSequence()
						.map { it.trim() }
						.filter { it.startsWith("package:") }
						.map { it.substringAfter("package:").trim() }
						.filter { it.isNotBlank() }
						.toList()

					AppDetails(
						versionName = versionName,
						enabled = enabled,
						isRunning = isRunning,
						apkPaths = apkPaths,
					)
				}
			}

			if (result.isSuccess) {
				detailsByPackage = detailsByPackage + (packageName to result.getOrThrow())
			}
		}
	}

	fun invalidateDetails(packageName: String) {
		if (!detailsByPackage.containsKey(packageName)) return
		detailsByPackage = detailsByPackage - packageName
	}

	suspend fun uninstall(packageName: String): Result<Unit> = runAction("pm uninstall $packageName")
	suspend fun clearData(packageName: String): Result<Unit> = runAction("pm clear $packageName")
	suspend fun forceStop(packageName: String): Result<Unit> = runAction("am force-stop $packageName")
	suspend fun launch(packageName: String): Result<Unit> = runAction("monkey -p $packageName -c android.intent.category.LAUNCHER 1")

	suspend fun setEnabled(packageName: String, enabled: Boolean): Result<Unit> {
		val cmd = if (enabled) "pm enable $packageName" else "pm disable-user $packageName"
		return runAction(cmd)
	}

	private suspend fun runAction(command: String): Result<Unit> {
		val id = deviceId ?: return Result.failure(IllegalStateException("deviceId 无效"))
		return withContext(ioDispatcher) {
			runCatching {
				val out = deviceManager.shell(id, command)
				if (out.isFailure) throw (out.exceptionOrNull() ?: IllegalStateException("命令执行失败"))
			}
		}
	}

	private suspend fun listPackages(deviceId: Long, userOnly: Boolean): List<AppItem> {
		val flag = if (userOnly) "-3" else "-s"
		val out = deviceManager.shell(deviceId, "pm list packages $flag -f").getOrNull().orEmpty()
		return out.lineSequence()
			.map { it.trim() }
			.filter { it.startsWith("package:") }
			.mapNotNull { line ->
				// format: package:/path/base.apk=com.example
				val rest = line.removePrefix("package:")
				val parts = rest.split('=')
				val apkPath = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
				val pkg = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
				AppItem(packageName = pkg, apkPath = apkPath, isSystem = !userOnly)
			}
			.toList()
	}

	private fun mergePackages(user: List<AppItem>, system: List<AppItem>): List<AppItem> {
		val byPkg = linkedMapOf<String, AppItem>()
		user.forEach { byPkg[it.packageName] = it }
		system.forEach { byPkg.putIfAbsent(it.packageName, it) }
		return byPkg.values.toList().sortedBy { it.packageName }
	}

	private suspend fun loadBottomInfo(deviceId: Long): BottomInfo {
		val storageText = runCatching {
			val df = deviceManager.shell(deviceId, "df -h /data").getOrNull().orEmpty()
			val line = df.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("Filesystem") }
			if (line == null) return@runCatching "剩余空间: -"
			val cols = line.split(Regex("\\s+")).filter { it.isNotBlank() }
			val size = cols.getOrNull(1)
			val avail = cols.getOrNull(3)
			if (size.isNullOrBlank() || avail.isNullOrBlank()) "剩余空间: -" else "剩余空间: $avail / $size"
		}.getOrDefault("剩余空间: -")

		val memoryText = runCatching {
			val meminfo = deviceManager.shell(deviceId, "cat /proc/meminfo").getOrNull().orEmpty()
			val totalKb = Regex("(?m)^MemTotal:\\s+(\\d+)\\s+kB").find(meminfo)?.groupValues?.getOrNull(1)?.toLongOrNull()
			val availKb = Regex("(?m)^MemAvailable:\\s+(\\d+)\\s+kB").find(meminfo)?.groupValues?.getOrNull(1)?.toLongOrNull()
			if (totalKb == null || availKb == null || totalKb <= 0) return@runCatching "内存占用: -"
			val usedPercent = ((1.0 - (availKb.toDouble() / totalKb.toDouble())) * 100.0).coerceIn(0.0, 100.0)
			"内存占用: ${usedPercent.roundToInt()}%"
		}.getOrDefault("内存占用: -")

		return BottomInfo(storageText = storageText, memoryText = memoryText)
	}
}
