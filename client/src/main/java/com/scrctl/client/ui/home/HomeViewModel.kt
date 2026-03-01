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

    var searchQuery by mutableStateOf("")
        private set
    
    var gridColumns by mutableStateOf(2)
        private set

    var isConnectedById by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set

    var connectionErrorById by mutableStateOf<Map<Long, String>>(emptyMap())
        private set
    
    init {
        observeGroups()
        observeDevices()
        observeConnectivity()
        observeConnectionErrors()
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
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectivity() {
        deviceManager.observeConnectedById()
            .onEach { snapshot ->
                isConnectedById = snapshot
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectionErrors() {
        deviceManager.observeErrorById()
            .onEach { snapshot ->
                connectionErrorById = snapshot
            }
            .launchIn(viewModelScope)
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

    /** 通过 [DeviceManager.getAgentClient] 拉取首页缩略图。 */
    suspend fun screencapThumbnailPng(deviceId: Long, width: Int, height: Int): Result<ByteArray> {
        return runCatching {
            val agentClient = deviceManager.getAgentClient(deviceId)
                ?: throw IllegalStateException("设备未连接或 Agent 不可用")
            agentClient.screenshot(width = width, height = height)
        }
    }



}