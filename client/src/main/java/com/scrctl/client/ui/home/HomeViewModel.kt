package com.scrctl.client.ui.home

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceManager: DeviceManager,
    @param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private var defaultsEnsured = false

    var groups by mutableStateOf(listOf<Group>())
        private set

    var selectedGroupId by mutableStateOf<Long?>(null)
        private set

    var allDevices by mutableStateOf(listOf<Device>())
        private set

    var devices by mutableStateOf(listOf<Device>())
        private set

    var batteryByDeviceId by mutableStateOf<Map<Long, Int?>>(emptyMap())
        private set

    var isConnectedById by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set

    private val batteryJobs = mutableMapOf<Long, Job>()
    private val batteryUpdatedAt = mutableMapOf<Long, Long>()
    
    var searchQuery by mutableStateOf("")
        private set
    
    var gridColumns by mutableStateOf(2)
        private set
    
    init {
        observeGroups()
        observeDevices()

        deviceManager.observeConnectedById()
            .onEach { map ->
                isConnectedById = map
                refreshBatteries(allDevices, map.filterValues { it }.keys)
            }
            .launchIn(viewModelScope)
    }

    fun selectGroup(groupId: Long?) {
        selectedGroupId = groupId
        recomputeFilteredDevices()
    }
    
    fun updateSearchQuery(query: String) {
        searchQuery = query
        recomputeFilteredDevices()
    }
    
    fun updateGridColumns(columns: Int) {
        gridColumns = columns
    }


    fun addGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val newId = withContext(ioDispatcher) {
                groupRepository.insertGroup(Group(name = trimmed))
            }
            selectGroup(newId)
        }
    }

    fun deleteGroup(group: Group) {
        if (group.id <= 0) return

        viewModelScope.launch {
            withContext(ioDispatcher) {
                deviceRepository.deleteDevicesByGroupId(group.id)
                groupRepository.deleteGroupById(group.id)
            }

            if (selectedGroupId == group.id) {
                selectedGroupId = null
                recomputeFilteredDevices()
            }
        }
    }

    private fun observeGroups() {
        groupRepository.getAllGroups()
            .onEach { groupList ->
                groups = groupList
                ensureDefaultGroupsIfNeeded(groupList)
            }
            .launchIn(viewModelScope)
    }

    private fun observeDevices() {
        deviceRepository.getAllDevices()
            .onEach { deviceList ->
                allDevices = deviceList
                recomputeFilteredDevices()
                refreshBatteries(deviceList, isConnectedById.filterValues { it }.keys)
            }
            .launchIn(viewModelScope)
    }

    private fun refreshBatteries(devices: List<Device>, connectedIds: Set<Long>) {

        // Remove stale entries/jobs for non-connected devices
        val toRemove = batteryByDeviceId.keys - connectedIds
        if (toRemove.isNotEmpty()) {
            val next = batteryByDeviceId.toMutableMap()
            toRemove.forEach {
                batteryJobs.remove(it)?.cancel()
                batteryUpdatedAt.remove(it)
                next.remove(it)
            }
            batteryByDeviceId = next
        }

        // Refresh connected devices (throttled)
        for (id in connectedIds) {
            if (batteryJobs[id]?.isActive == true) continue
            val now = System.currentTimeMillis()
            val last = batteryUpdatedAt[id] ?: 0L
            if (now - last < 20_000) continue

            batteryJobs[id] = viewModelScope.launch {
                val out = withContext(ioDispatcher) {
                    deviceManager.shell(id, "dumpsys battery")
                }

                val percent = if (out.isSuccess) {
                    parseBatteryPercent(out.getOrNull().orEmpty())
                } else {
                    null
                }

                val next = batteryByDeviceId.toMutableMap()
                next[id] = percent
                batteryByDeviceId = next
                batteryUpdatedAt[id] = System.currentTimeMillis()
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

    private fun ensureDefaultGroupsIfNeeded(current: List<Group>) {
        if (defaultsEnsured) return
        if (current.isNotEmpty()) {
            defaultsEnsured = true
            return
        }

        defaultsEnsured = true
        viewModelScope.launch {
            withContext(ioDispatcher) {
                groupRepository.insertGroup(Group(name = "默认分组"))
            }
        }
    }

    private fun recomputeFilteredDevices() {
        val query = searchQuery.trim()
        val base = when (val gid = selectedGroupId) {
            null -> allDevices
            else -> allDevices.filter { it.groupId == gid }
        }

        devices = if (query.isEmpty()) {
            base
        } else {
            base.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.deviceAddr.contains(query, ignoreCase = true) ||
                    it.id.toString().contains(query)
            }
        }
    }

    /** 代理 [DeviceManager.screencapPng]，供 Composable 使用。 */
    suspend fun screencapPng(deviceId: Long): Result<ByteArray> {
        return deviceManager.screencapPng(deviceId)
    }
}