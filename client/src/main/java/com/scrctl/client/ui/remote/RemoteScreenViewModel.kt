package com.scrctl.client.ui.remote

import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrctl.client.BuildConfig
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.devicemanager.DeviceManager
import com.scrctl.client.core.repository.DeviceRepository
import com.scrctl.client.core.scrcpy.Controller
import com.scrctl.client.core.scrcpy.Scrcpy
import com.scrctl.client.core.scrcpy.Scrcpy.SessionEvent
import com.scrctl.client.core.scrcpy.Scrcpy.SessionIssue
import com.scrctl.client.core.scrcpy.Scrcpy.SessionStage
import com.scrctl.client.core.scrcpy.Scrcpy.SessionTermination
import com.scrctl.client.core.scrcpy.Scrcpy.StageStatus
import com.scrctl.client.core.scrcpy.Server
import com.scrctl.client.core.scrcpy.TouchPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RemoteScreenViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val deviceManager: DeviceManager,
    private val deviceRepository: DeviceRepository,
    @param:Dispatcher(ScrctlDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteScreenUiState())
    val uiState: StateFlow<RemoteScreenUiState> = _uiState.asStateFlow()

    private val connectedGamepads = linkedMapOf<Int, String>()

    @Volatile
    private var activeDeviceId: Long? = null

    @Volatile
    private var controllerReady: Boolean = false

    @Volatile
    private var currentSurface: Surface? = null

    private var currentScrcpy: Scrcpy? = null
    private var sessionEventJob: Job? = null

    private val inputManager by lazy(LazyThreadSafetyMode.NONE) {
        RemoteScreenInputManager(
            tag = TAG,
            canSendControllerEvents = ::canSendControllerEvents,
            logBlockedControllerEvent = ::logBlockedControllerEvent,
            launchControllerAction = ::launchControllerAction,
        )
    }
    private val serverRuntime by lazy(LazyThreadSafetyMode.NONE) {
        Server.Runtime.resolve(
            appContext = appContext,
            versionName = BuildConfig.VERSION_NAME,
        )
    }

    fun onEnter(deviceId: Long) {
        Log.d(TAG, "onEnter(deviceId=$deviceId)")
        beginConnectionAttempt(
            deviceId = deviceId,
            connectingText = "正在准备远程连接...",
            failureText = "远程连接准备失败",
            onFailureLog = { Log.e(TAG, "Failed to enter remote control for device=$deviceId", it) },
        )
    }

    fun onExit(deviceId: Long) {
        Log.d(TAG, "onExit(deviceId=$deviceId)")
        markControllerUnavailable()
        if (activeDeviceId == deviceId) {
            activeDeviceId = null
            _uiState.value = _uiState.value.copy(
                uiStatus = UiStatus.IDLE,
                statusText = "",
                isFirstFrameReady = false,
                remoteRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            )
        }
        shutdownCurrentScrcpyAsync()
    }

    fun onSurfaceReady(deviceId: Long, surface: Surface) {
        Log.d(TAG, "onSurfaceReady(deviceId=$deviceId)")
        currentSurface = surface
        viewModelScope.launch {
            withContext(ioDispatcher) {
                currentScrcpy?.attachSurface(surface)
            }
            syncConnectedState(deviceId)
        }
    }

    fun onSurfaceDestroyed(deviceId: Long) {
        Log.d(TAG, "onSurfaceDestroyed(deviceId=$deviceId)")
        currentSurface = null
        viewModelScope.launch {
            withContext(ioDispatcher) {
                currentScrcpy?.detachSurface()
            }
        }
    }

    fun onTouch(
        deviceId: Long,
        action: Int,
        actionIndex: Int,
        pointers: List<TouchPoint>,
        surfaceWidth: Int,
        surfaceHeight: Int,
        actionButton: Int,
        buttons: Int,
    ) {
        inputManager.onTouch(
            deviceId = deviceId,
            action = action,
            actionIndex = actionIndex,
            pointers = pointers,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            actionButton = actionButton,
            buttons = buttons,
        )
    }

    fun onScroll(
        deviceId: Long,
        x: Int,
        y: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int,
    ) {
        inputManager.onScroll(
            deviceId = deviceId,
            x = x,
            y = y,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            hScroll = hScroll,
            vScroll = vScroll,
            buttons = buttons,
        )
    }

    fun sendBack(deviceId: Long) {
        inputManager.sendBack(deviceId)
    }

    fun sendHome(deviceId: Long) {
        inputManager.sendHome(deviceId)
    }

    fun sendRecent(deviceId: Long) {
        inputManager.sendRecent(deviceId)
    }

    fun onRemoteKey(deviceId: Long, keyCode: Int, action: Int) {
        inputManager.onRemoteKey(deviceId, keyCode, action)
    }

    fun retryConnection() {
        val deviceId = activeDeviceId ?: return
        if (_uiState.value.uiStatus != UiStatus.DISCONNECTED && _uiState.value.uiStatus != UiStatus.STREAM_ERROR) {
            return
        }

        beginConnectionAttempt(
            deviceId = deviceId,
            connectingText = "正在重新建立连接...",
            failureText = "重新建立连接失败",
        )
    }

    fun syncGamepads(gamepads: List<Pair<Int, String>>) {
        connectedGamepads.clear()
        for ((id, name) in gamepads) {
            connectedGamepads[id] = name
        }
        _uiState.value = _uiState.value.copy(
            connectedGamepadCount = connectedGamepads.size,
            gamepadStatusText = if (connectedGamepads.size > 0) {
                "手柄已连接: ${connectedGamepads.size}"
            } else {
                ""
            }
        )
    }

    fun onGamepadAdded(deviceId: Int, deviceName: String) {
        connectedGamepads[deviceId] = deviceName
        _uiState.value = _uiState.value.copy(
            connectedGamepadCount = connectedGamepads.size,
            gamepadStatusText = "手柄已连接: $deviceName"
        )
    }

    fun onGamepadRemoved(deviceId: Int) {
        val name = connectedGamepads.remove(deviceId)
        _uiState.value = _uiState.value.copy(
            connectedGamepadCount = connectedGamepads.size,
            gamepadStatusText = if (name != null) {
                "手柄已断开: $name"
            } else if (connectedGamepads.size > 0) {
                "手柄已连接: ${connectedGamepads.size}"
            } else {
                ""
            }
        )
    }

    fun registerRemoteGamepad(deviceId: Long, localGamepadId: Int, name: String) {
        inputManager.registerRemoteGamepad(deviceId, localGamepadId, name)
    }

    fun unregisterRemoteGamepad(deviceId: Long, localGamepadId: Int) {
        inputManager.unregisterRemoteGamepad(deviceId, localGamepadId)
    }

    fun onRemoteGamepadKey(
        deviceId: Long,
        localGamepadId: Int,
        keyCode: Int,
        action: Int,
    ) {
        inputManager.onRemoteGamepadKey(deviceId, localGamepadId, keyCode, action)
    }

    fun onRemoteGamepadMotion(
        deviceId: Long,
        localGamepadId: Int,
        lx: Float,
        ly: Float,
        rx: Float,
        ry: Float,
    ) {
        inputManager.onRemoteGamepadMotion(deviceId, localGamepadId, lx, ly, rx, ry)
    }

    private fun onControllerReady(deviceId: Long) {
        if (activeDeviceId != deviceId) {
            Log.w(TAG, "Ignore controller ready for inactive device=$deviceId, active=$activeDeviceId")
            return
        }
        Log.i(TAG, "Controller ready for device=$deviceId")
        controllerReady = true
        inputManager.onControllerReady(deviceId)
        syncConnectedState(deviceId)
    }

    private fun syncConnectedState(deviceId: Long) {
        if (activeDeviceId == deviceId && controllerReady && currentSurface != null) {
            _uiState.value = _uiState.value.copy(
                uiStatus = UiStatus.CONNECTED,
                statusText = ""
            )
        }
    }

    private fun markControllerUnavailable() {
        Log.d(TAG, "Controller unavailable")
        controllerReady = false
        inputManager.markControllerUnavailable()
    }

    private fun canSendControllerEvents(deviceId: Long): Boolean {
        return activeDeviceId == deviceId && controllerReady
    }

    private fun logBlockedControllerEvent(kind: String, deviceId: Long) {
        Log.w(
            TAG,
            "Skip $kind event: target=$deviceId active=$activeDeviceId controllerReady=$controllerReady hasScrcpy=${currentScrcpy != null}",
        )
    }

    private fun setCurrentScrcpy(scrcpy: Scrcpy) {
        sessionEventJob?.cancel()
        Log.d(TAG, "setCurrentScrcpy()")
        currentScrcpy = scrcpy
        currentSurface?.let { surface ->
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    currentScrcpy?.attachSurface(surface)
                }
            }
        }
        sessionEventJob = viewModelScope.launch {
            scrcpy.observeSessionEvents().collect { event ->
                when (event) {
                    is SessionEvent.StageChanged -> {
                        val status = mapStepStatus(event.status)
                        updateConnectionStep(
                            step = event.stage,
                            status = status,
                            detail = buildStepDetail(event.stage, status, event.error),
                        )
                        if (event.stage == SessionStage.DECODE_SUCCESS && status == StepStatus.SUCCESS) {
                            _uiState.value = _uiState.value.copy(isFirstFrameReady = true)
                        }
                    }
                    is SessionEvent.Ended -> {
                        markControllerUnavailable()
                        _uiState.value = _uiState.value.copy(
                            isFirstFrameReady = false,
                            uiStatus = UiStatus.DISCONNECTED,
                            statusText = buildTerminationText(event.termination)
                        )
                    }
                    is SessionEvent.IssueOccurred -> {
                        markControllerUnavailable()
                        _uiState.value = _uiState.value.copy(
                            isFirstFrameReady = false,
                            uiStatus = mapUiStatus(event.issue),
                            statusText = buildIssueText(event.issue)
                        )
                    }
                    is SessionEvent.DisplayRotationChanged -> {
                        _uiState.value = _uiState.value.copy(
                            remoteRequestedOrientation = mapRequestedOrientation(event.rotation)
                        )
                    }
                    is SessionEvent.StreamEnded -> Unit
                }
            }
        }
    }

    private fun mapRequestedOrientation(rotation: Int): Int {
        return when (rotation and 0x3) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            3 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun beginConnectionAttempt(
        deviceId: Long,
        connectingText: String,
        failureText: String,
        onFailureLog: ((Throwable) -> Unit)? = null,
    ) {
        activeDeviceId = deviceId
        markControllerUnavailable()
        resetConnectionSteps()
        _uiState.value = _uiState.value.copy(
            isFirstFrameReady = false,
            uiStatus = UiStatus.CONNECTING,
            statusText = connectingText
        )
        launchScrcpyStart(
            deviceId = deviceId,
            failureText = failureText,
            onFailureLog = onFailureLog,
        )
    }

    private fun launchScrcpyStart(
        deviceId: Long,
        failureText: String,
        onFailureLog: ((Throwable) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                stopAndCloseCurrentScrcpy()
                clearCurrentScrcpy()

                val scrcpy = withContext(ioDispatcher) {
                    createScrcpy(deviceId)
                }
                val device = withContext(ioDispatcher) {
                    deviceRepository.getDeviceById(deviceId)
                }
                setCurrentScrcpy(scrcpy)
                withContext(ioDispatcher) {
                    scrcpy.start(buildScrcpyOptions(device))
                }
                onControllerReady(deviceId)
            }.onFailure { error ->
                onFailureLog?.invoke(error)
                markControllerUnavailable()
                _uiState.value = _uiState.value.copy(
                    uiStatus = UiStatus.DISCONNECTED,
                    statusText = error.message ?: failureText
                )
                clearCurrentScrcpy()
            }
        }
    }

    private fun launchControllerAction(
        kind: String,
        errorMessage: String,
        onFailure: (() -> Unit)? = null,
        action: (Controller) -> Unit,
    ) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching {
                    val controller = currentScrcpy?.getController()
                        ?: error("Controller unavailable for $kind")
                    action(controller)
                }.onFailure { throwable ->
                    onFailure?.invoke()
                    Log.e(TAG, errorMessage, throwable)
                }
            }
        }
    }

    private fun clearCurrentScrcpy() {
        sessionEventJob?.cancel()
        sessionEventJob = null
        currentScrcpy = null
    }

    private fun shutdownCurrentScrcpyAsync() {
        viewModelScope.launch {
            stopAndCloseCurrentScrcpy()
            clearCurrentScrcpy()
        }
    }

    private suspend fun stopAndCloseCurrentScrcpy() {
        withContext(ioDispatcher) {
            currentScrcpy?.stop()
            currentScrcpy?.close()
        }
    }

    private fun createScrcpy(deviceId: Long): Scrcpy {
        val kadb = deviceManager.getAdbClient(deviceId)
            ?: throw IllegalStateException("设备未连接")
        return Scrcpy(
            kadb = kadb,
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            serverRuntime = serverRuntime,
        )
    }

    private fun buildScrcpyOptions(device: Device?): Scrcpy.ScrcpyOptions {
        val metrics = appContext.resources.displayMetrics
        return Scrcpy.ScrcpyOptions(
            video = device?.streamVideoEnabled ?: true,
            audio = device?.streamAudioEnabled ?: true,
            requireAudio = device?.streamRequireAudio ?: false,
            videoBitRate = device?.streamVideoBitRate ?: 8_000_000,
            audioBitRate = device?.streamAudioBitRate ?: 128_000,
            maxSize = device?.streamMaxSize ?: 0,
            videoCodec = device?.streamVideoCodec ?: "h264",
            audioCodec = device?.streamAudioCodec ?: "aac",
            wmWidth = metrics.widthPixels,
            wmHeight = metrics.heightPixels,
            wmDensity = metrics.densityDpi,
        )
    }

    override fun onCleared() {
        super.onCleared()
        sessionEventJob?.cancel()
        runCatching {
            currentScrcpy?.stop()
            currentScrcpy?.close()
        }
        currentScrcpy = null
    }

    private fun resetConnectionSteps() {
        val newSteps = CONNECTION_STEP_ORDER.map { step ->
            ConnectionStepUi(
                step = step,
                title = STEP_TEXTS.getValue(step).title,
                status = StepStatus.PENDING,
                detail = "",
            )
        }
        _uiState.value = _uiState.value.copy(
            showConnectionSteps = true,
            connectionSteps = newSteps
        )
    }

    private fun updateConnectionStep(
        step: SessionStage,
        status: StepStatus,
        detail: String,
    ) {
        val current = _uiState.value.connectionSteps
        if (current.isEmpty()) {
            resetConnectionSteps()
        }

        val updatedSteps = _uiState.value.connectionSteps.map { item ->
            if (item.step == step) {
                item.copy(
                    status = status,
                    detail = detail,
                )
            } else {
                item
            }
        }
        
        _uiState.value = _uiState.value.copy(connectionSteps = updatedSteps)

        if (step == SessionStage.DECODE_SUCCESS && status == StepStatus.SUCCESS) {
            _uiState.value = _uiState.value.copy(showConnectionSteps = false)
        }
    }

    private fun mapStepStatus(status: StageStatus): StepStatus {
        return when (status) {
            StageStatus.STARTED -> StepStatus.RUNNING
            StageStatus.SUCCESS -> StepStatus.SUCCESS
            StageStatus.FAILED -> StepStatus.FAILED
        }
    }

    private fun mapUiStatus(issue: SessionIssue): UiStatus {
        return ISSUE_TO_UI_STATUS.getValue(issue)
    }

    private fun buildIssueText(issue: SessionIssue): String {
        return ISSUE_TEXTS.getValue(issue)
    }

    private fun buildTerminationText(termination: SessionTermination): String {
        return TERMINATION_TEXTS.getValue(termination)
    }

    private fun buildStepDetail(
        step: SessionStage,
        status: StepStatus,
        error: Throwable?,
    ): String {
        val texts = STEP_TEXTS.getValue(step)
        return when (status) {
            StepStatus.PENDING -> ""
            StepStatus.RUNNING -> texts.running
            StepStatus.SUCCESS -> texts.success
            StepStatus.FAILED -> error?.message ?: texts.failed
        }
    }

    private companion object {
        private const val TAG = "RemoteScreenVM"

        val STEP_TEXTS = mapOf(
            SessionStage.PUSH_SERVER_JAR to StepTextSet(
                title = "准备连接组件",
                running = "正在准备连接组件",
                success = "连接组件已准备完成",
                failed = "连接组件准备失败",
            ),
            SessionStage.START_SERVER_SERVICE to StepTextSet(
                title = "建立设备连接",
                running = "正在建立设备连接",
                success = "设备连接已建立",
                failed = "设备连接建立失败",
            ),
            SessionStage.FIRST_VIDEO_PACKET to StepTextSet(
                title = "同步设备画面",
                running = "正在等待设备画面",
                success = "设备画面已同步",
                failed = "设备画面同步失败",
            ),
            SessionStage.START_DECODE to StepTextSet(
                title = "优化画面流畅度",
                running = "正在优化画面流畅度",
                success = "画面传输已优化",
                failed = "画面优化失败",
            ),
            SessionStage.DECODE_SUCCESS to StepTextSet(
                title = "连接准备完成",
                success = "已进入远程控制",
                failed = "远程控制准备失败",
            ),
        )

        val ISSUE_TO_UI_STATUS = mapOf(
            SessionIssue.STREAM to UiStatus.STREAM_ERROR,
            SessionIssue.CONTROL to UiStatus.DISCONNECTED,
        )

        val ISSUE_TEXTS = mapOf(
            SessionIssue.STREAM to "设备画面传输异常",
            SessionIssue.CONTROL to "设备控制通道异常",
        )

        val TERMINATION_TEXTS = mapOf(
            SessionTermination.CONNECTION_LOST to "连接已断开",
        )

        val CONNECTION_STEP_ORDER = listOf(
            SessionStage.PUSH_SERVER_JAR,
            SessionStage.START_SERVER_SERVICE,
            SessionStage.FIRST_VIDEO_PACKET,
            SessionStage.START_DECODE,
            SessionStage.DECODE_SUCCESS,
        )
    }
}

private class RemoteScreenInputManager(
    private val tag: String,
    private val canSendControllerEvents: (Long) -> Boolean,
    private val logBlockedControllerEvent: (String, Long) -> Unit,
    private val launchControllerAction: (
        kind: String,
        errorMessage: String,
        onFailure: (() -> Unit)?,
        action: (Controller) -> Unit,
    ) -> Unit,
) {
    private data class RemoteGamepadState(
        val uhidId: Int,
        var name: String,
        var created: Boolean = false,
        var buttons: Int = 0,
        var dpadUp: Boolean = false,
        var dpadDown: Boolean = false,
        var dpadLeft: Boolean = false,
        var dpadRight: Boolean = false,
        var lx: Int = AXIS_CENTER,
        var ly: Int = AXIS_CENTER,
        var rx: Int = AXIS_CENTER,
        var ry: Int = AXIS_CENTER,
    )

    private val remoteGamepads = linkedMapOf<Int, RemoteGamepadState>()

    fun onTouch(
        deviceId: Long,
        action: Int,
        actionIndex: Int,
        pointers: List<TouchPoint>,
        surfaceWidth: Int,
        surfaceHeight: Int,
        actionButton: Int,
        buttons: Int,
    ) {
        val activePointer = pointers.getOrNull(actionIndex)
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            Log.d(
                tag,
                "Send touch: device=$deviceId action=${touchActionName(action)} pointers=${pointers.size} actionPointer=${activePointer?.pointerId} size=${surfaceWidth}x$surfaceHeight buttons=$buttons",
            )
        }
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "touch",
            kind = "touch",
            errorMessage = "Failed to send touch event: device=$deviceId action=${touchActionName(action)} pointers=${pointers.size}",
            onFailure = null,
        ) { controller ->
            controller.injectMultiTouchEvent(
                action = action,
                actionIndex = actionIndex,
                pointers = pointers,
                screenWidth = surfaceWidth,
                screenHeight = surfaceHeight,
                actionButton = actionButton,
                buttons = buttons,
            )
        }
    }

    fun onScroll(
        deviceId: Long,
        x: Int,
        y: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int,
    ) {
        Log.d(tag, "Send scroll: device=$deviceId x=$x y=$y h=$hScroll v=$vScroll buttons=$buttons")
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "scroll",
            kind = "scroll",
            errorMessage = "Failed to send scroll event: device=$deviceId x=$x y=$y",
            onFailure = null,
        ) { controller ->
            controller.injectScrollEvent(
                x = x,
                y = y,
                screenWidth = surfaceWidth,
                screenHeight = surfaceHeight,
                hScroll = hScroll,
                vScroll = vScroll,
                buttons = buttons,
            )
        }
    }

    fun onRemoteKey(deviceId: Long, keyCode: Int, action: Int) {
        Log.d(tag, "Send key: device=$deviceId action=${keyActionName(action)} keyCode=$keyCode")
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "key",
            kind = "key",
            errorMessage = "Failed to send key event: device=$deviceId action=${keyActionName(action)} keyCode=$keyCode",
            onFailure = null,
        ) { controller ->
            controller.injectKeycode(
                action = action,
                keycode = keyCode,
                repeat = 0,
                metaState = 0,
            )
        }
    }

    fun sendBack(deviceId: Long) {
        sendKey(deviceId, KeyEvent.KEYCODE_BACK)
    }

    fun sendHome(deviceId: Long) {
        sendKey(deviceId, KeyEvent.KEYCODE_HOME)
    }

    fun sendRecent(deviceId: Long) {
        sendKey(deviceId, KeyEvent.KEYCODE_APP_SWITCH)
    }

    fun registerRemoteGamepad(deviceId: Long, localGamepadId: Int, name: String) {
        val state = remoteGamepads.getOrPut(localGamepadId) {
            RemoteGamepadState(
                uhidId = toUhidId(localGamepadId),
                name = name,
            )
        }
        state.name = name
        createRemoteGamepadIfNeeded(deviceId, state)
    }

    fun unregisterRemoteGamepad(deviceId: Long, localGamepadId: Int) {
        val state = remoteGamepads.remove(localGamepadId) ?: return
        if (!state.created) {
            return
        }
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "gamepad-destroy",
            kind = "gamepad-destroy",
            errorMessage = "Failed to destroy remote gamepad: device=$deviceId uhid=${state.uhidId}",
            onFailure = null,
        ) { controller ->
            controller.uhidDestroy(state.uhidId)
        }
    }

    fun onRemoteGamepadKey(
        deviceId: Long,
        localGamepadId: Int,
        keyCode: Int,
        action: Int,
    ) {
        val state = remoteGamepads[localGamepadId] ?: return
        if (!canSendControllerEvents(deviceId)) {
            logBlockedControllerEvent("gamepad-key", deviceId)
            return
        }
        if (!state.created) {
            return
        }
        val pressed = action != KeyEvent.ACTION_UP
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> updateButton(state, BUTTON_A, pressed)
            KeyEvent.KEYCODE_BUTTON_B -> updateButton(state, BUTTON_B, pressed)
            KeyEvent.KEYCODE_BUTTON_X -> updateButton(state, BUTTON_X, pressed)
            KeyEvent.KEYCODE_BUTTON_Y -> updateButton(state, BUTTON_Y, pressed)
            KeyEvent.KEYCODE_BUTTON_L1 -> updateButton(state, BUTTON_L1, pressed)
            KeyEvent.KEYCODE_BUTTON_R1 -> updateButton(state, BUTTON_R1, pressed)
            KeyEvent.KEYCODE_BUTTON_SELECT -> updateButton(state, BUTTON_SELECT, pressed)
            KeyEvent.KEYCODE_BUTTON_START -> updateButton(state, BUTTON_START, pressed)
            KeyEvent.KEYCODE_BUTTON_THUMBL -> updateButton(state, BUTTON_THUMBL, pressed)
            KeyEvent.KEYCODE_BUTTON_THUMBR -> updateButton(state, BUTTON_THUMBR, pressed)
            KeyEvent.KEYCODE_DPAD_UP -> state.dpadUp = pressed
            KeyEvent.KEYCODE_DPAD_DOWN -> state.dpadDown = pressed
            KeyEvent.KEYCODE_DPAD_LEFT -> state.dpadLeft = pressed
            KeyEvent.KEYCODE_DPAD_RIGHT -> state.dpadRight = pressed
            else -> return
        }

        sendGamepadReport(deviceId, state)
    }

    fun onRemoteGamepadMotion(
        deviceId: Long,
        localGamepadId: Int,
        lx: Float,
        ly: Float,
        rx: Float,
        ry: Float,
    ) {
        val state = remoteGamepads[localGamepadId] ?: return
        if (!canSendControllerEvents(deviceId)) {
            logBlockedControllerEvent("gamepad-motion", deviceId)
            return
        }
        if (!state.created) {
            return
        }
        state.lx = axisToByte(lx)
        state.ly = axisToByte(ly)
        state.rx = axisToByte(rx)
        state.ry = axisToByte(ry)
        sendGamepadReport(deviceId, state)
    }

    fun onControllerReady(deviceId: Long) {
        remoteGamepads.values.forEach { state ->
            createRemoteGamepadIfNeeded(deviceId, state)
        }
    }

    fun markControllerUnavailable() {
        remoteGamepads.values.forEach { it.created = false }
    }

    private fun sendKey(deviceId: Long, keyCode: Int) {
        Log.d(tag, "Send key click: device=$deviceId keyCode=$keyCode")
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "key-click",
            kind = "key-click",
            errorMessage = "Failed to send key click: device=$deviceId keyCode=$keyCode",
            onFailure = null,
        ) { controller ->
            controller.pressKeycode(keyCode)
        }
    }

    private fun sendGamepadReport(deviceId: Long, state: RemoteGamepadState) {
        if (!state.created) {
            return
        }
        val report = buildReport(state)
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "gamepad-report",
            kind = "gamepad-report",
            errorMessage = "Failed to send gamepad report: device=$deviceId uhid=${state.uhidId}",
            onFailure = null,
        ) { controller ->
            controller.uhidInput(state.uhidId, report)
        }
    }

    private fun createRemoteGamepadIfNeeded(deviceId: Long, state: RemoteGamepadState) {
        if (state.created) {
            return
        }
        state.created = true
        dispatchControllerAction(
            deviceId = deviceId,
            blockedKind = "gamepad-create",
            kind = "gamepad-create",
            errorMessage = "Failed to create remote gamepad: device=$deviceId uhid=${state.uhidId}",
            onFailure = { state.created = false },
        ) { controller ->
            controller.uhidCreate(
                id = state.uhidId,
                vendorId = DEFAULT_VENDOR_ID,
                productId = DEFAULT_PRODUCT_ID,
                name = "Android ${state.name}",
                reportDesc = GAMEPAD_REPORT_DESCRIPTOR,
            )
            controller.uhidInput(state.uhidId, buildReport(state))
        }
    }

    private fun dispatchControllerAction(
        deviceId: Long,
        blockedKind: String,
        kind: String,
        errorMessage: String,
        onFailure: (() -> Unit)?,
        action: (Controller) -> Unit,
    ) {
        if (!canSendControllerEvents(deviceId)) {
            logBlockedControllerEvent(blockedKind, deviceId)
            return
        }
        launchControllerAction(kind, errorMessage, onFailure, action)
    }

    private fun buildReport(state: RemoteGamepadState): ByteArray {
        val hat = hatFromDpad(state)
        return byteArrayOf(
            REPORT_ID.toByte(),
            (state.buttons and 0xFF).toByte(),
            ((state.buttons ushr 8) and 0xFF).toByte(),
            hat.toByte(),
            state.lx.toByte(),
            state.ly.toByte(),
            state.rx.toByte(),
            state.ry.toByte(),
        )
    }

    private fun hatFromDpad(state: RemoteGamepadState): Int {
        val up = state.dpadUp
        val down = state.dpadDown
        val left = state.dpadLeft
        val right = state.dpadRight

        return when {
            up && right -> HAT_UP_RIGHT
            down && right -> HAT_DOWN_RIGHT
            down && left -> HAT_DOWN_LEFT
            up && left -> HAT_UP_LEFT
            up -> HAT_UP
            right -> HAT_RIGHT
            down -> HAT_DOWN
            left -> HAT_LEFT
            else -> HAT_CENTER
        }
    }

    private fun axisToByte(value: Float): Int {
        val clamped = value.coerceIn(-1f, 1f)
        return ((clamped + 1f) * 127.5f).toInt().coerceIn(0, 255)
    }

    private fun updateButton(state: RemoteGamepadState, mask: Int, pressed: Boolean) {
        state.buttons = if (pressed) {
            state.buttons or mask
        } else {
            state.buttons and mask.inv()
        }
    }

    private fun toUhidId(localGamepadId: Int): Int {
        return (localGamepadId and 0x7FFF).coerceAtLeast(1)
    }

    private fun touchActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> action.toString()
        }
    }

    private fun keyActionName(action: Int): String {
        return when (action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> action.toString()
        }
    }

    private companion object {
        private const val REPORT_ID = 1
        private const val AXIS_CENTER = 128
        private const val DEFAULT_VENDOR_ID = 0x18D1
        private const val DEFAULT_PRODUCT_ID = 0x2C40

        private const val BUTTON_A = 1 shl 0
        private const val BUTTON_B = 1 shl 1
        private const val BUTTON_X = 1 shl 2
        private const val BUTTON_Y = 1 shl 3
        private const val BUTTON_L1 = 1 shl 4
        private const val BUTTON_R1 = 1 shl 5
        private const val BUTTON_SELECT = 1 shl 6
        private const val BUTTON_START = 1 shl 7
        private const val BUTTON_THUMBL = 1 shl 8
        private const val BUTTON_THUMBR = 1 shl 9

        private const val HAT_UP = 0
        private const val HAT_UP_RIGHT = 1
        private const val HAT_RIGHT = 2
        private const val HAT_DOWN_RIGHT = 3
        private const val HAT_DOWN = 4
        private const val HAT_DOWN_LEFT = 5
        private const val HAT_LEFT = 6
        private const val HAT_UP_LEFT = 7
        private const val HAT_CENTER = 8

        private val GAMEPAD_REPORT_DESCRIPTOR = byteArrayOf(
            0x05, 0x01,
            0x09, 0x05,
            0xA1.toByte(), 0x01,
            0x85.toByte(), REPORT_ID.toByte(),
            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x10,
            0x15, 0x00,
            0x25, 0x01,
            0x95.toByte(), 0x10,
            0x75, 0x01,
            0x81.toByte(), 0x02,
            0x05, 0x01,
            0x09, 0x39,
            0x15, 0x00,
            0x25, 0x07,
            0x35, 0x00,
            0x46, 0x3B, 0x01,
            0x65, 0x14,
            0x75, 0x04,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x42,
            0x75, 0x04,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x03,
            0x09, 0x30,
            0x09, 0x31,
            0x09, 0x33,
            0x09, 0x34,
            0x15, 0x00,
            0x26, 0xFF.toByte(), 0x00,
            0x75, 0x08,
            0x95.toByte(), 0x04,
            0x81.toByte(), 0x02,
            0xC0.toByte(),
        )
    }
}
