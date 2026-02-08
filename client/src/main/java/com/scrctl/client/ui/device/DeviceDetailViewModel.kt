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

    var device by mutableStateOf<Device?>(null)
        private set

    var group by mutableStateOf<Group?>(null)
        private set

    var groups by mutableStateOf(listOf<Group>())
        private set

    var batteryPercent by mutableStateOf<Int?>(null)
        private set

    var batteryLoading by mutableStateOf(false)
        private set

    var isConnected by mutableStateOf(false)
        private set

    private var observeJob: Job? = null
    private var connectivityJob: Job? = null
    private var batteryUpdatedAt: Long = 0L

    init {
        groupRepository.getAllGroups()
            .onEach { groups = it }
            .launchIn(viewModelScope)
    }

    fun load(deviceId: Long) {
        observeJob?.cancel()
        connectivityJob?.cancel()

        observeJob = deviceRepository.observeDeviceById(deviceId)
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { d ->
                device = d
                group = if (d.groupId > 0) {
                    withContext(ioDispatcher) { groupRepository.getGroupById(d.groupId) }
                } else {
                    null
                }
            }
            .launchIn(viewModelScope)

        connectivityJob = viewModelScope.launch {
            deviceManager.observeIsConnected(deviceId).collect { ok ->
                isConnected = ok
                val current = device
                if (ok && current != null) {
                    refreshBatteryIfNeeded(current)
                } else if (!ok) {
                    batteryPercent = null
                    batteryLoading = false
                }
            }
        }
    }

    private fun refreshBatteryIfNeeded(device: Device) {
        val now = System.currentTimeMillis()
        if (batteryLoading) return
        if (now - batteryUpdatedAt < 15_000) return

        batteryLoading = true
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                deviceManager.shell(device.id, "dumpsys battery")
            }

            batteryLoading = false
            if (result.isSuccess) {
                batteryPercent = parseBatteryPercent(result.getOrNull().orEmpty())
                batteryUpdatedAt = System.currentTimeMillis()
            } else {
                batteryPercent = null
            }
        }
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
            device = updated
            group = if (updated.groupId > 0) {
                withContext(ioDispatcher) { groupRepository.getGroupById(updated.groupId) }
            } else {
                null
            }
        }
    }

    suspend fun deleteDevice(deviceId: Long): Result<Unit> {
        return runCatching {
            withContext(ioDispatcher) {
                deviceRepository.deleteDeviceById(deviceId)
            }
        }
    }
}
