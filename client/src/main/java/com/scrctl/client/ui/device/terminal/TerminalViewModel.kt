package com.scrctl.client.ui.device.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val savedStateHandle: SavedStateHandle,
    @param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    enum class LineType { COMMAND, OUTPUT, ERROR }

    data class TerminalLine(
        val text: String,
        val type: LineType,
    )

    val lines = mutableStateListOf<TerminalLine>()

    var isRunning by mutableStateOf(false)
        private set

    private var deviceId: Long? = savedStateHandle.get<String>("deviceId")?.toLongOrNull()

    fun setDeviceId(id: Long) {
        if (deviceId == id && lines.isNotEmpty()) return
        deviceId = id
        savedStateHandle["deviceId"] = id
        lines.clear()
        lines.add(TerminalLine("scrctl terminal  (device: $id)", LineType.OUTPUT))
        lines.add(TerminalLine("type 'exit' or press back to close", LineType.OUTPUT))
        lines.add(TerminalLine("", LineType.OUTPUT))
    }

    fun runCommand(input: String) {
        val id = deviceId ?: return
        val cmd = input.trim()
        if (cmd.isEmpty()) return

        lines.add(TerminalLine("$ $cmd", LineType.COMMAND))

        if (cmd == "clear") {
            lines.clear()
            return
        }

        isRunning = true
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val adb = deviceManager.getAdbClient(id)
                        ?: throw IllegalStateException("设备未连接")
                    adb.shell(cmd)
                }
            }
            result
                .onSuccess { resp ->
                    val out = resp.output.trimEnd('\n')
                    val err = resp.errorOutput.trimEnd('\n')
                    if (out.isNotEmpty()) {
                        out.lines().forEach { lines.add(TerminalLine(it, LineType.OUTPUT)) }
                    }
                    if (err.isNotEmpty()) {
                        err.lines().forEach { lines.add(TerminalLine(it, LineType.ERROR)) }
                    }
                    if (out.isEmpty() && err.isEmpty()) {
                        lines.add(TerminalLine("", LineType.OUTPUT))
                    }
                }
                .onFailure { e ->
                    lines.add(TerminalLine(e.message ?: "命令执行失败", LineType.ERROR))
                }
            isRunning = false
        }
    }
}
