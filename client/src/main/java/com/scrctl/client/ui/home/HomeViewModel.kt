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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Legacy properties for backward compatibility
    val groups: List<Group> get() = _uiState.value.groups
    val devices: List<Device> get() = _uiState.value.devices

    init {
        observeGroups()
        observeDevices()
        observeConnectivity()
        observeConnectionErrors()
    }

    fun selectGroup(groupId: Long?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedGroupId = groupId)
            recomputeFilteredDevices()
        }
    }

    fun updateSearchQuery(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query)
            recomputeFilteredDevices()
        }
    }
    
    fun updateGridColumns(columns: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(gridColumns = columns)
        }
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

            if (_uiState.value.selectedGroupId == group.id) {
                selectGroup(null)
            }
        }
    }

    private fun observeGroups() {
        groupRepository.getAllGroups()
            .onEach { groupList ->
                _uiState.value = _uiState.value.copy(groups = groupList)
                ensureDefaultGroupsIfNeeded(groupList)
            }
            .launchIn(viewModelScope)
    }

    private fun observeDevices() {
        deviceRepository.getAllDevices()
            .onEach { deviceList ->
                _uiState.value = _uiState.value.copy(allDevices = deviceList)
                recomputeFilteredDevices()
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectivity() {
        deviceManager.observeConnectedById()
            .onEach { snapshot ->
                _uiState.value = _uiState.value.copy(isConnectedById = snapshot)
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectionErrors() {
        deviceManager.observeErrorById()
            .onEach { snapshot ->
                _uiState.value = _uiState.value.copy(connectionErrorById = snapshot)
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
        val currentState = _uiState.value
        val query = currentState.searchQuery.trim()
        val base = when (val gid = currentState.selectedGroupId) {
            null -> currentState.allDevices
            else -> currentState.allDevices.filter { it.groupId == gid }
        }

        val filteredDevices = if (query.isEmpty()) {
            base
        } else {
            base.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.deviceAddr.contains(query, ignoreCase = true) ||
                    it.id.toString().contains(query)
            }
        }
        
        _uiState.value = currentState.copy(devices = filteredDevices)
    }

    /** 通过 DeviceManager 获取 Kadb 并执行 scrcpy server 拉取首页缩略图。 */
    suspend fun screencapThumbnailPng(deviceId: Long, width: Int, height: Int): Result<ByteArray> {
        return deviceManager.screencapThumbnailPng(deviceId, width, height)
    }

}
