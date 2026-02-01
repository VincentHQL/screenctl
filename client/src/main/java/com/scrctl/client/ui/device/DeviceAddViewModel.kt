package com.scrctl.client.ui.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flyfishxu.kadb.Kadb
import com.scrctl.client.core.devicemanager.DeviceConnectionState
import com.scrctl.client.core.devicemanager.DeviceManager
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group
import com.scrctl.client.core.repository.DeviceRepository
import com.scrctl.client.core.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class DeviceAddViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceManager: DeviceManager,
    @Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

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
        method: ConnectionMethod,
        deviceName: String,
        adbPort: Int,
        ipAddress: String,
    ): Long {
        val trimmedName = deviceName.trim()

        val entity = when (method) {
            ConnectionMethod.DIRECT -> {
                Device(
                    groupId = groupId,
                    connectMode = 1,
                    deviceAddr = ipAddress.trim(),
                    devicePort = adbPort,
                    name = if (trimmedName.isNotEmpty()) trimmedName else ipAddress.trim(),
                )
            }

            ConnectionMethod.WIRELESS -> {
                Device(
                    groupId = groupId,
                    connectMode = 2,
                    deviceAddr = ipAddress.trim(),
                    devicePort = adbPort,
                    name = if (trimmedName.isNotEmpty()) trimmedName else ipAddress.trim(),
                )
            }
        }

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
        timeoutMs: Long = 15_000,
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
                    method = method,
                    deviceName = deviceName,
                    adbPort = adbPort,
                    ipAddress = host,
                )

                deviceManager.reconnect(id)

                try {
                    val finalDevice = waitForFinalState(id, timeoutMs)
                    if (finalDevice.connectionState == DeviceConnectionState.CONNECTED.name) {
                        id
                    } else {
                        val msg = finalDevice.connectionError.takeIf { it.isNotBlank() } ?: "连接失败"
                        deviceRepository.deleteDeviceById(id)
                        throw IllegalStateException(msg)
                    }
                } catch (t: Throwable) {
                    // timeout or other errors -> rollback
                    deviceRepository.deleteDeviceById(id)
                    if (t is TimeoutCancellationException) {
                        throw IllegalStateException("连接超时")
                    }
                    throw t
                }
            }
        }
    }

    private suspend fun waitForFinalState(deviceId: Long, timeoutMs: Long): Device {
        return withTimeout(timeoutMs) {
            deviceRepository.observeDeviceById(deviceId)
                .filterNotNull()
                .first { d ->
                    d.connectionState == DeviceConnectionState.CONNECTED.name ||
                        d.connectionState == DeviceConnectionState.ERROR.name
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
