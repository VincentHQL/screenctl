package com.scrctl.client.ui.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flyfishxu.kadb.Kadb
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group
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
class DeviceAddViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceRepository: DeviceRepository,
    @param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val defaultGroupName = "默认分组"

    var groups by mutableStateOf(listOf<Group>())
        private set

    private var defaultsEnsured = false

    init {
        groupRepository.getAllGroups()
            .onEach { list ->
                groups = list
                ensureDefaultGroupIfNeeded(list)
            }
            .launchIn(viewModelScope)
    }

    private suspend fun addDeviceInternal(
        groupId: Long,
        deviceName: String,
        adbPort: Int,
        ipAddress: String,
    ): Long {
        val trimmedName = deviceName.trim()

        val entity = Device(
                    groupId = groupId,
                    deviceAddr = ipAddress.trim(),
                    devicePort = adbPort,
                    name = if (trimmedName.isNotEmpty()) trimmedName else ipAddress.trim(),
                )

        return withContext(ioDispatcher) {
            deviceRepository.insertDevice(entity)
        }
    }

    suspend fun connectAndAddDevice(
        groupId: Long,
        method: ConnectionMethod,
        deviceName: String,
        ipAddress: String,
        adbPort: Int,
        pairingPort: Int = 0,
        pairingCode: String = "",
    ): Result<Long> {
        val host = ipAddress.trim()
        val trimmedPairCode = pairingCode.trim()

        return withContext(ioDispatcher) {
            runCatching {
                if (host.isBlank()) throw IllegalArgumentException("IP 地址为空")
                if (adbPort !in 1..65535) throw IllegalArgumentException("ADB 端口不合法(1-65535)")

                if (method == ConnectionMethod.WIRELESS) {
                    if (pairingPort !in 1..65535) throw IllegalArgumentException("配对端口不合法(1-65535)")
                    if (trimmedPairCode.isBlank()) throw IllegalArgumentException("配对码为空")
                    Kadb.pair(host, pairingPort, trimmedPairCode)
                }

                val id = addDeviceInternal(
                    groupId = groupId,
                    deviceName = deviceName,
                    adbPort = adbPort,
                    ipAddress = host,
                )

                id
            }
        }
    }

    private fun ensureDefaultGroupIfNeeded(current: List<Group>) {
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
}
