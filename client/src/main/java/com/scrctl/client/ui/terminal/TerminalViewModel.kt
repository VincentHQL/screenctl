package com.scrctl.client.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.devicemanager.DeviceManager
import com.scrctl.client.core.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
	private val deviceManager: DeviceManager,
	private val deviceRepository: DeviceRepository,
	@param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

	private val _uiState = MutableStateFlow(TerminalUiState())
	val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

	private var deviceJob: Job? = null
	private var connectivityJob: Job? = null
	private var nextEntryId: Long = 0L

	fun bind(deviceId: Long) {
		if (_uiState.value.deviceId == deviceId && (deviceJob?.isActive == true || connectivityJob?.isActive == true)) {
			return
		}

		deviceJob?.cancel()
		connectivityJob?.cancel()
		nextEntryId = 0L

		_uiState.value = TerminalUiState(
			deviceId = deviceId,
			entries = buildBannerEntries(deviceId),
			suggestedCommands = DEFAULT_COMMANDS,
		)

		deviceJob = deviceRepository.observeDeviceById(deviceId)
			.filterNotNull()
			.distinctUntilChanged()
			.onEach { device ->
				val label = device.name.takeIf { it.isNotBlank() }
					?: "${device.deviceAddr}:${device.devicePort}"
				_uiState.update { state ->
					state.copy(deviceLabel = label)
				}
			}
			.launchIn(viewModelScope)

		var lastConnection: Boolean? = null
		connectivityJob = deviceManager.observeIsConnected(deviceId)
			.distinctUntilChanged()
			.onEach { connected ->
				val previous = lastConnection
				lastConnection = connected
				_uiState.update { state ->
					state.copy(isConnected = connected)
				}

				when {
					previous == true && !connected -> appendEntry(
						kind = TerminalLineKind.Error,
						text = "设备连接已断开，命令发送已禁用。",
					)

					previous == false && connected -> appendEntry(
						kind = TerminalLineKind.System,
						text = "设备已重新连接。",
					)
				}
			}
			.launchIn(viewModelScope)
	}

	fun clearSession() {
		val deviceId = _uiState.value.deviceId ?: return
		nextEntryId = 0L
		_uiState.update { state ->
			state.copy(
				entries = buildBannerEntries(deviceId),
				lastError = null,
			)
		}
	}

	fun runCommand(rawCommand: String) {
		val command = rawCommand.trim()
		if (command.isEmpty() || _uiState.value.isExecuting) {
			return
		}

		when (command.lowercase()) {
			"clear", "cls" -> {
				clearSession()
				return
			}

			"help" -> {
				appendEntry(
					kind = TerminalLineKind.System,
					text = HELP_TEXT,
				)
				rememberCommand(command)
				return
			}
		}

		val deviceId = _uiState.value.deviceId ?: return
		if (!_uiState.value.isConnected) {
			appendEntry(
				kind = TerminalLineKind.Error,
				text = "设备未连接，无法执行命令。",
			)
			return
		}

		if (command == "cd" || command.startsWith("cd ") || command.startsWith("export ")) {
			appendEntry(
				kind = TerminalLineKind.System,
				text = "提示：每条命令都会独立执行，目录和环境变量不会保留到下一条命令。",
			)
		}

		appendEntry(
			kind = TerminalLineKind.Command,
			text = command,
		)
		_uiState.update { state ->
			state.copy(
				isExecuting = true,
				lastError = null,
			)
		}
		rememberCommand(command)

		viewModelScope.launch {
			val result = withContext(ioDispatcher) {
				runCatching {
					val adbClient = deviceManager.getAdbClient(deviceId)
						?: throw IllegalStateException("设备未连接")
					adbClient.shell(command).allOutput
				}
			}

			result.onSuccess { output ->
				val normalized = output.trimEnd()
				appendEntry(
					kind = TerminalLineKind.Output,
					text = normalized.ifBlank { "命令执行完成，无输出。" },
				)
			}.onFailure { throwable ->
				val message = throwable.message?.ifBlank { null } ?: "命令执行失败"
				_uiState.update { state ->
					state.copy(lastError = message)
				}
				appendEntry(
					kind = TerminalLineKind.Error,
					text = message,
				)
			}

			_uiState.update { state ->
				state.copy(isExecuting = false)
			}
		}
	}

	private fun rememberCommand(command: String) {
		_uiState.update { state ->
			val recent = buildList {
				add(command)
				state.recentCommands
					.asSequence()
					.filterNot { it.equals(command, ignoreCase = true) }
					.take(7)
					.forEach(::add)
			}
			state.copy(recentCommands = recent)
		}
	}

	private fun buildBannerEntries(deviceId: Long): List<TerminalLine> {
		return listOf(
			newEntry(
				kind = TerminalLineKind.System,
				text = "screenctl shell 已连接，设备 ID: $deviceId",
			),
			newEntry(
				kind = TerminalLineKind.System,
				text = "输入 Android shell 命令后发送。输入 help 查看说明，输入 clear 清空会话。",
			),
		)
	}

	private fun appendEntry(kind: TerminalLineKind, text: String) {
		_uiState.update { state ->
			val updated = (state.entries + newEntry(kind, text)).takeLast(MAX_ENTRIES)
			state.copy(entries = updated)
		}
	}

	private fun newEntry(kind: TerminalLineKind, text: String): TerminalLine {
		nextEntryId += 1L
		return TerminalLine(
			id = nextEntryId,
			kind = kind,
			text = text,
		)
	}

	companion object {
		private const val MAX_ENTRIES = 200

		private val DEFAULT_COMMANDS = listOf(
			"getprop ro.product.model",
			"getprop ro.build.version.release",
			"wm size",
			"df -h /data",
			"pm list packages -3",
			"settings get system screen_brightness",
		)

		private val HELP_TEXT = listOf(
			"这是一个类 adb shell 的命令面板。",
			"- 每次发送都会单独执行一条 shell 命令。",
			"- cd/export 之类的状态不会在下一条命令中保留。",
			"- 可以把多条命令写在同一行，例如: cd /sdcard; ls",
		).joinToString(separator = "\n")
	}
}

data class TerminalUiState(
	val deviceId: Long? = null,
	val deviceLabel: String = "",
	val isConnected: Boolean = false,
	val isExecuting: Boolean = false,
	val entries: List<TerminalLine> = emptyList(),
	val recentCommands: List<String> = emptyList(),
	val suggestedCommands: List<String> = emptyList(),
	val lastError: String? = null,
)

data class TerminalLine(
	val id: Long,
	val kind: TerminalLineKind,
	val text: String,
)

enum class TerminalLineKind {
	Command,
	Output,
	System,
	Error,
}
