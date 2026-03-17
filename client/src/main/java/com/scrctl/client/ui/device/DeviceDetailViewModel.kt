package com.scrctl.client.ui.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group
import com.scrctl.client.core.devicemanager.DeviceManager
import com.scrctl.client.core.repository.DeviceRepository
import com.scrctl.client.core.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val groupRepository: GroupRepository,
    private val deviceManager: DeviceManager,
    @param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    // Legacy properties for backward compatibility
    val device: Device? get() = _uiState.value.device
    val group: Group? get() = _uiState.value.group
    val groups: List<Group> get() = _uiState.value.groups

    private var observeJob: Job? = null
    private var connectivityJob: Job? = null
    private var batteryUpdatedAt: Long = 0L
    private var deviceInfoUpdatedAt: Long = 0L

    init {
        groupRepository.getAllGroups()
            .onEach { groupsList -> 
                _uiState.value = _uiState.value.copy(groups = groupsList)
            }
            .launchIn(viewModelScope)
    }

    fun load(deviceId: Long) {
        observeJob?.cancel()
        connectivityJob?.cancel()

        observeJob = deviceRepository.observeDeviceById(deviceId)
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { d ->
                val deviceGroup = if (d.groupId > 0) {
                    withContext(ioDispatcher) { groupRepository.getGroupById(d.groupId) }
                } else {
                    null
                }
                _uiState.value = _uiState.value.copy(
                    device = d,
                    group = deviceGroup
                )
            }
            .launchIn(viewModelScope)

        connectivityJob = viewModelScope.launch {
            deviceManager.observeIsConnected(deviceId).collect { ok ->
                _uiState.value = _uiState.value.copy(isConnected = ok)
                val current = _uiState.value.device
                if (ok && current != null) {
                    refreshBatteryIfNeeded(current)
                    refreshDeviceInfoIfNeeded(current)
                } else if (!ok) {
                    _uiState.value = _uiState.value.copy(
                        batteryPercent = null,
                        batteryLoading = false
                    )
                }
            }
        }
    }

    private fun refreshBatteryIfNeeded(device: Device) {
        val now = System.currentTimeMillis()
        if (_uiState.value.batteryLoading) return
        if (now - batteryUpdatedAt < 15_000) return

        _uiState.value = _uiState.value.copy(batteryLoading = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                shell(device.id, "dumpsys battery")
            }

            _uiState.value = _uiState.value.copy(batteryLoading = false)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    batteryPercent = parseBatteryPercent(result.getOrNull().orEmpty())
                )
            } else {
                _uiState.value = _uiState.value.copy(batteryPercent = null)
            }
        }
    }

    private fun refreshDeviceInfoIfNeeded(device: Device) {
        val now = System.currentTimeMillis()
        val currentState = _uiState.value
        if (currentState.deviceInfoLoading) return
        if (currentState.deviceModel != null && currentState.systemVersion != null && now - deviceInfoUpdatedAt < 60_000) return

        _uiState.value = _uiState.value.copy(deviceInfoLoading = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                shell(
                    device.id,
                    "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk"
                )
            }

            _uiState.value = _uiState.value.copy(deviceInfoLoading = false)
            if (result.isSuccess) {
                val parsed = parseDeviceInfo(result.getOrNull().orEmpty())
                _uiState.value = _uiState.value.copy(
                    deviceModel = parsed.model,
                    systemVersion = parsed.systemVersion
                )
                deviceInfoUpdatedAt = System.currentTimeMillis()
            }
        }
    }

    private data class ParsedDeviceInfo(
        val model: String?,
        val systemVersion: String?,
    )

    private fun parseDeviceInfo(output: String): ParsedDeviceInfo {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val manufacturer = lines.getOrNull(0)
        val model = lines.getOrNull(1)
        val release = lines.getOrNull(2)
        val sdk = lines.getOrNull(3)

        val normalizedModel = when {
            model.isNullOrBlank() -> null
            manufacturer.isNullOrBlank() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }

        val normalizedSystemVersion = when {
            release.isNullOrBlank() && sdk.isNullOrBlank() -> null
            release.isNullOrBlank() -> "Android SDK $sdk"
            sdk.isNullOrBlank() -> "Android $release"
            else -> "Android $release (SDK $sdk)"
        }

        return ParsedDeviceInfo(
            model = normalizedModel,
            systemVersion = normalizedSystemVersion,
        )
    }

    private fun parseBatteryPercent(output: String): Int? {
        val level = Regex("(?m)^\\s*level:\\s*(\\d+)\\s*$").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val scale = Regex("(?m)^\\s*scale:\\s*(\\d+)\\s*$").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return when {
            level == null -> null
            scale != null && scale > 0 -> ((level.toDouble() / scale.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            else -> level.coerceIn(0, 100)
        }
    }

    suspend fun updateDevice(updated: Device): Result<Unit> {
        return runCatching {
            withContext(ioDispatcher) {
                deviceRepository.updateDevice(updated)
            }
            val updatedGroup = if (updated.groupId > 0) {
                withContext(ioDispatcher) { groupRepository.getGroupById(updated.groupId) }
            } else {
                null
            }
            _uiState.value = _uiState.value.copy(
                device = updated,
                group = updatedGroup
            )
        }
    }

    suspend fun saveStreamConfig(
        videoEnabled: Boolean,
        audioEnabled: Boolean,
        requireAudio: Boolean,
        videoBitRate: Int,
        audioBitRate: Int,
        maxSize: Int,
        videoCodec: String,
        audioCodec: String,
    ): Result<Unit> {
        val current = device ?: return Result.failure(IllegalStateException("设备不存在"))
        _uiState.value = _uiState.value.copy(isSavingStreamConfig = true)
        return runCatching {
            withContext(ioDispatcher) {
                deviceRepository.updateDevice(
                    current.copy(
                        streamVideoEnabled = videoEnabled,
                        streamAudioEnabled = audioEnabled,
                        streamRequireAudio = requireAudio,
                        streamVideoBitRate = videoBitRate,
                        streamAudioBitRate = audioBitRate,
                        streamMaxSize = maxSize,
                        streamVideoCodec = videoCodec,
                        streamAudioCodec = audioCodec,
                    )
                )
            }
        }.also {
            _uiState.value = _uiState.value.copy(isSavingStreamConfig = false)
        }
    }

    suspend fun deleteDevice(deviceId: Long): Result<Unit> {
        return runCatching {
            withContext(ioDispatcher) {
                deviceRepository.deleteDeviceById(deviceId)
            }
        }
    }

    private fun shell(deviceId: Long, command: String): Result<String> {
        return runCatching {
            val adbClient = deviceManager.getAdbClient(deviceId)
                ?: throw IllegalStateException("设备未连接")
            adbClient.shell(command).allOutput
        }
    }
}
