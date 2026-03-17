package com.scrctl.client.ui.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.core.scrcpy.TouchPoint
import kotlin.math.roundToInt

private const val EXIT_BACK_PRESS_INTERVAL_MS = 1200L
private const val TOOLBAR_AUTO_COLLAPSE_MS = 4000L

@Immutable
private data class RemoteConnectingPalette(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val primary: Color,
    val accent: Color,
    val outline: Color,
    val textSecondary: Color,
    val warning: Color,
    val previewTile: Color,
    val previewTileRaised: Color,
)

@Composable
private fun rememberRemoteConnectingPalette(): RemoteConnectingPalette {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        RemoteConnectingPalette(
            background = Color(0xFF000000),
            backgroundElevated = Color(0xFF060A0B),
            surface = Color(0xCC101F22),
            surfaceRaised = Color(0xE60B1518),
            primary = Color(0xFF0DCCF2),
            accent = Color(0xFF8DEAF8),
            outline = Color(0xFF0DCCF2).copy(alpha = 0.18f),
            textSecondary = Color(0xFFA7B4B8),
            warning = colorScheme.error.copy(alpha = 0.9f),
            previewTile = Color(0xFF434C50),
            previewTileRaised = Color(0xFF2B3438),
        )
    }
}

@Composable
fun RemoteScreen(
    viewModel: RemoteScreenViewModel = hiltViewModel(),
    deviceId: String,
    onExit: () -> Unit,
) {
    val connectingPalette = rememberRemoteConnectingPalette()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val parsedDeviceId = remember(deviceId) { deviceId.toLongOrNull() }
    
    val uiState by viewModel.uiState.collectAsState()
    val uiStatus = uiState.uiStatus
    val statusText = uiState.statusText
    val gamepadStatusText = uiState.gamepadStatusText
    val showConnectionSteps = uiState.showConnectionSteps
    val connectionSteps = uiState.connectionSteps
    val isFirstFrameReady = uiState.isFirstFrameReady
    val remoteRequestedOrientation = uiState.remoteRequestedOrientation
    val canRetry = uiStatus == UiStatus.DISCONNECTED ||
        uiStatus == UiStatus.STREAM_ERROR
    val showConnectingOverlay = uiStatus != UiStatus.CONNECTED || !isFirstFrameReady
    var lastBackPressAt by remember { mutableStateOf(0L) }
    var toolbarExpanded by remember { mutableStateOf(false) }
    var toolbarOffsetX by remember { mutableStateOf(0f) }
    var toolbarOffsetY by remember { mutableStateOf(0f) }
    val toolbarAlpha by animateFloatAsState(
        targetValue = if (toolbarExpanded) 0.92f else 0.55f,
        animationSpec = tween(durationMillis = 220),
        label = "remoteToolbarAlpha",
    )
    val remoteSurfaceAlpha by animateFloatAsState(
        targetValue = if (showConnectingOverlay) 0.18f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "remoteSurfaceAlpha",
    )

    LaunchedEffect(toolbarExpanded) {
        if (toolbarExpanded) {
            kotlinx.coroutines.delay(TOOLBAR_AUTO_COLLAPSE_MS)
            toolbarExpanded = false
        }
    }

    BackHandler {
        val remoteId = parsedDeviceId
        if (uiStatus == UiStatus.CONNECTED && remoteId != null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackPressAt <= EXIT_BACK_PRESS_INTERVAL_MS) {
                onExit()
            } else {
                lastBackPressAt = now
                viewModel.sendBack(remoteId)
            }
        } else {
            onExit()
        }
    }

    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }

    DisposableEffect(activity, view) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = insetsController?.systemBarsBehavior
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (previousBehavior != null) {
                insetsController.systemBarsBehavior = previousBehavior
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    LaunchedEffect(activity, remoteRequestedOrientation) {
        if (activity != null && remoteRequestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = remoteRequestedOrientation
        }
    }

    DisposableEffect(context) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

        fun isGamepad(device: InputDevice?): Boolean {
            if (device == null || device.isVirtual) {
                return false
            }
            val sources = device.sources
            val isPad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            return isPad || isJoystick
        }

        val initial = InputDevice.getDeviceIds().toList()
            .mapNotNull { id ->
                val device = InputDevice.getDevice(id)
                if (device != null && isGamepad(device)) {
                    parsedDeviceId?.let { remoteId ->
                        viewModel.registerRemoteGamepad(remoteId, id, device.name)
                    }
                    id to device.name
                } else {
                    null
                }
            }
        viewModel.syncGamepads(initial)

        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                val device = InputDevice.getDevice(deviceId)
                if (device != null && isGamepad(device)) {
                    parsedDeviceId?.let { remoteId ->
                        viewModel.registerRemoteGamepad(remoteId, deviceId, device.name)
                    }
                    viewModel.onGamepadAdded(deviceId, device.name)
                }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                parsedDeviceId?.let { remoteId ->
                    viewModel.unregisterRemoteGamepad(remoteId, deviceId)
                }
                viewModel.onGamepadRemoved(deviceId)
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                val device = InputDevice.getDevice(deviceId)
                if (device != null && isGamepad(device)) {
                    parsedDeviceId?.let { remoteId ->
                        viewModel.registerRemoteGamepad(remoteId, deviceId, device.name)
                    }
                    viewModel.onGamepadAdded(deviceId, device.name)
                } else {
                    parsedDeviceId?.let { remoteId ->
                        viewModel.unregisterRemoteGamepad(remoteId, deviceId)
                    }
                    viewModel.onGamepadRemoved(deviceId)
                }
            }
        }

        inputManager.registerInputDeviceListener(listener, null)
        onDispose {
            inputManager.unregisterInputDeviceListener(listener)
        }
    }

    DisposableEffect(parsedDeviceId) {
        parsedDeviceId?.let { viewModel.onEnter(it) }
        onDispose {
            parsedDeviceId?.let {
                viewModel.onSurfaceDestroyed(it)
                viewModel.onExit(it)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (showConnectingOverlay) connectingPalette.background else MaterialTheme.colorScheme.background)
    ) {
        RemoteScreenSurfaceView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(remoteSurfaceAlpha),
            onSurfaceReady = { surface -> parsedDeviceId?.let { viewModel.onSurfaceReady(it, surface) } },
            onSurfaceDestroyed = { parsedDeviceId?.let { viewModel.onSurfaceDestroyed(it) } },
            onGamepadKey = { localGamepadId, keyCode, action ->
                parsedDeviceId?.let { remoteId ->
                    viewModel.onRemoteGamepadKey(remoteId, localGamepadId, keyCode, action)
                }
            },
            onGamepadMotion = { localGamepadId, lx, ly, rx, ry ->
                parsedDeviceId?.let { remoteId ->
                    viewModel.onRemoteGamepadMotion(remoteId, localGamepadId, lx, ly, rx, ry)
                }
            },
            onKey = { keyCode, action ->
                parsedDeviceId?.let { remoteId ->
                    handleRemoteScreenHardwareKey(viewModel, remoteId, keyCode, action)
                } ?: false
            },
            onTouch = { action, actionIndex, pointers, surfaceWidth, surfaceHeight, actionButton, buttons ->
                parsedDeviceId?.let { remoteId ->
                    viewModel.onTouch(
                        deviceId = remoteId,
                        action = action,
                        actionIndex = actionIndex,
                        pointers = pointers,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        actionButton = actionButton,
                        buttons = buttons,
                    )
                }
            },
            onScroll = { x, y, surfaceWidth, surfaceHeight, hScroll, vScroll, buttons ->
                parsedDeviceId?.let { remoteId ->
                    viewModel.onScroll(
                        deviceId = remoteId,
                        x = x,
                        y = y,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        hScroll = hScroll,
                        vScroll = vScroll,
                        buttons = buttons,
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = showConnectingOverlay,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 320)),
        ) {
            RemoteConnectingOverlay(
                palette = connectingPalette,
                uiStatus = uiStatus,
                statusText = statusText,
                steps = if (showConnectionSteps) connectionSteps else emptyList(),
                canRetry = canRetry,
                onRetry = viewModel::retryConnection,
                onExit = onExit,
            )
        }

        if (!showConnectingOverlay) {
            RemoteGamepadBadge(gamepadStatusText)

            FloatingRemoteToolbar(
                expanded = toolbarExpanded,
                alpha = toolbarAlpha,
                offsetX = toolbarOffsetX,
                offsetY = toolbarOffsetY,
                onDrag = { dx: Float, dy: Float ->
                    toolbarOffsetX += dx
                    toolbarOffsetY += dy
                },
                onToggleExpanded = { toolbarExpanded = !toolbarExpanded },
                onBack = { parsedDeviceId?.let(viewModel::sendBack) },
                onHome = { parsedDeviceId?.let(viewModel::sendHome) },
                onRecent = { parsedDeviceId?.let(viewModel::sendRecent) },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun BoxScope.RemoteConnectingOverlay(
    palette: RemoteConnectingPalette,
    uiStatus: UiStatus,
    statusText: String,
    steps: List<ConnectionStepUi>,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val headline = remember(uiStatus) { buildConnectingHeadline(uiStatus) }
    val description = remember(uiStatus, statusText, steps) {
        buildConnectingDescription(uiStatus, statusText, steps)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.background, palette.backgroundElevated),
                )
            )
    ) {
        ConnectingPreviewBackground(palette)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.42f),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectingPulseIndicator(palette)
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = headline,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }

        if (canRetry) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionActionButton(
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    label = "返回",
                    icon = Icons.Filled.Warning,
                    emphasis = false,
                    onClick = onExit,
                )
                ConnectionActionButton(
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    label = "重新连接",
                    icon = Icons.Filled.Refresh,
                    emphasis = true,
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun ConnectingPreviewBackground(palette: RemoteConnectingPalette) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.background,
                        palette.backgroundElevated,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(16.dp)
                .alpha(0.2f)
                .blur(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(2) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    repeat(2) { columnIndex ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if ((rowIndex + columnIndex) % 2 == 0) {
                                        palette.previewTile
                                    } else {
                                        palette.previewTileRaised
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectingPulseIndicator(palette: RemoteConnectingPalette) {
    val pulse = rememberInfiniteTransition(label = "connectingPulse")
    val outerAlpha by pulse.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectingOuterAlpha",
    )

    Box(
        modifier = Modifier.size(192.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(192.dp)
                .border(1.dp, palette.primary.copy(alpha = outerAlpha), RoundedCornerShape(96.dp))
        )
        Box(
            modifier = Modifier
                .size(144.dp)
                .border(1.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(72.dp))
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .rotate(45f)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.primary.copy(alpha = 0.1f))
                .border(1.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier
                    .size(36.dp)
                    .rotate(-45f),
            )
        }
    }
}

@Composable
private fun ConnectionActionButton(
    palette: RemoteConnectingPalette,
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    emphasis: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (emphasis) {
                    palette.primary.copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.04f)
                }
            )
            .border(
                1.dp,
                if (emphasis) palette.primary.copy(alpha = 0.28f) else palette.outline,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasis) palette.accent else palette.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (emphasis) Color.White else palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun buildConnectingHeadline(uiStatus: UiStatus): String {
    return when (uiStatus) {
        UiStatus.CONNECTING -> "正在连接到设备"
        UiStatus.DISCONNECTED -> "视频连接已中断"
        UiStatus.STREAM_ERROR -> "视频流接入异常"
        UiStatus.IDLE -> "正在连接到设备"
        UiStatus.CONNECTED -> "连接成功"
    }
}

private fun buildConnectingDescription(
    uiStatus: UiStatus,
    statusText: String,
    steps: List<ConnectionStepUi>,
): String {
    val stepText = steps.firstOrNull {
        it.status == StepStatus.FAILED ||
            it.status == StepStatus.RUNNING
    }?.let { step ->
        step.detail.ifBlank { step.title }
    } ?: steps.firstOrNull()?.detail?.ifBlank { steps.firstOrNull()?.title.orEmpty() }

    return when (uiStatus) {
        UiStatus.CONNECTING -> statusText.ifBlank {
            stepText?.takeIf { it.isNotBlank() } ?: "正在为您准备流畅的操控体验，请稍后..."
        }
        UiStatus.DISCONNECTED,
        UiStatus.STREAM_ERROR -> statusText.ifBlank { "视频流建立失败，请重新连接或返回设备详情。" }
        UiStatus.IDLE -> statusText.ifBlank {
            stepText?.takeIf { it.isNotBlank() } ?: "正在为您准备流畅的操控体验，请稍后..."
        }
        UiStatus.CONNECTED -> statusText
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun handleRemoteScreenHardwareKey(
    viewModel: RemoteScreenViewModel,
    deviceId: Long,
    keyCode: Int,
    action: Int,
): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE -> {
            viewModel.onRemoteKey(deviceId = deviceId, keyCode = keyCode, action = action)
            true
        }

        else -> false
    }
}

@Composable
private fun RemoteScreenSurfaceView(
    modifier: Modifier = Modifier,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: (() -> Unit)? = null,
    onGamepadKey: ((deviceId: Int, keyCode: Int, action: Int) -> Unit)? = null,
    onGamepadMotion: ((deviceId: Int, lx: Float, ly: Float, rx: Float, ry: Float) -> Unit)? = null,
    onKey: ((keyCode: Int, action: Int) -> Boolean)? = null,
    onTouch: ((action: Int, actionIndex: Int, pointers: List<TouchPoint>, surfaceWidth: Int, surfaceHeight: Int, actionButton: Int, buttons: Int) -> Unit)? = null,
    onScroll: ((x: Int, y: Int, surfaceWidth: Int, surfaceHeight: Int, hScroll: Float, vScroll: Float, buttons: Int) -> Unit)? = null,
) {
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    var surfaceNotifiedReady by remember { mutableStateOf(false) }
    val onSurfaceReadyState = rememberUpdatedState(onSurfaceReady)
    val onSurfaceDestroyedState = rememberUpdatedState(onSurfaceDestroyed)
    val onGamepadKeyState = rememberUpdatedState(onGamepadKey)
    val onGamepadMotionState = rememberUpdatedState(onGamepadMotion)
    val onKeyState = rememberUpdatedState(onKey)
    val onTouchState = rememberUpdatedState(onTouch)
    val onScrollState = rememberUpdatedState(onScroll)

    LaunchedEffect(currentSurface) {
        val surface = currentSurface
        if (surface != null && !surfaceNotifiedReady) {
            onSurfaceReadyState.value.invoke(surface)
            surfaceNotifiedReady = true
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                object : SurfaceView(context) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (onKeyState.value?.invoke(event.keyCode, event.action) == true) {
                            return true
                        }

                        val source = event.source
                        val isPad = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                        val isJoy = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                        if (isPad || isJoy) {
                            onGamepadKeyState.value?.invoke(event.deviceId, event.keyCode, event.action)
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }

                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        val callback = onTouchState.value ?: return super.onTouchEvent(event)
                        val surfaceWidth = width
                        val surfaceHeight = height
                        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                            return super.onTouchEvent(event)
                        }

                        val action = when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_POINTER_DOWN,
                            MotionEvent.ACTION_MOVE,
                            MotionEvent.ACTION_POINTER_UP,
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> event.actionMasked
                            else -> return super.onTouchEvent(event)
                        }

                        val pointers = ArrayList<TouchPoint>(event.pointerCount)
                        for (index in 0 until event.pointerCount) {
                            pointers += TouchPoint(
                                pointerId = event.getPointerId(index).toLong(),
                                x = event.getX(index).toInt().coerceIn(0, surfaceWidth),
                                y = event.getY(index).toInt().coerceIn(0, surfaceHeight),
                                pressure = event.getPressure(index).coerceIn(0f, 1f),
                            )
                        }

                        callback(
                            action,
                            event.actionIndex.coerceIn(0, pointers.lastIndex),
                            pointers,
                            surfaceWidth,
                            surfaceHeight,
                            event.actionButton,
                            event.buttonState,
                        )
                        return true
                    }

                    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                        val surfaceWidth = width
                        val surfaceHeight = height
                        if (event.action == MotionEvent.ACTION_SCROLL && surfaceWidth > 0 && surfaceHeight > 0) {
                            onScrollState.value?.invoke(
                                event.x.toInt().coerceIn(0, surfaceWidth),
                                event.y.toInt().coerceIn(0, surfaceHeight),
                                surfaceWidth,
                                surfaceHeight,
                                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                                event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                                event.buttonState,
                            )
                            return true
                        }

                        val source = event.source
                        val isJoy = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                        if (isJoy && event.action == MotionEvent.ACTION_MOVE) {
                            val lx = event.getAxisValue(MotionEvent.AXIS_X)
                            val ly = event.getAxisValue(MotionEvent.AXIS_Y)
                            val rxRaw = event.getAxisValue(MotionEvent.AXIS_Z)
                            val ryRaw = event.getAxisValue(MotionEvent.AXIS_RZ)
                            val rx = if (rxRaw == 0f) event.getAxisValue(MotionEvent.AXIS_RX) else rxRaw
                            val ry = if (ryRaw == 0f) event.getAxisValue(MotionEvent.AXIS_RY) else ryRaw
                            onGamepadMotionState.value?.invoke(event.deviceId, lx, ly, rx, ry)
                            return true
                        }
                        return super.onGenericMotionEvent(event)
                    }
                }.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    isLongClickable = true
                    requestFocus()
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            currentSurface = holder.surface
                            surfaceNotifiedReady = false
                            requestFocus()
                            onSurfaceReadyState.value.invoke(holder.surface)
                            surfaceNotifiedReady = true
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            currentSurface = null
                            surfaceNotifiedReady = false
                            onSurfaceDestroyedState.value?.invoke()
                        }
                    })
                }
            },
        )
    }
}

@Composable
private fun BoxScope.RemoteGamepadBadge(gamepadStatusText: String) {
    if (gamepadStatusText.isBlank()) {
        return
    }

    Text(
        text = gamepadStatusText,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 16.dp, top = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun BoxScope.FloatingRemoteToolbar(
    expanded: Boolean,
    alpha: Float,
    offsetX: Float,
    offsetY: Float,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onToggleExpanded: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecent: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(end = 16.dp, bottom = 24.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        horizontalAlignment = Alignment.End,
    ) {
        if (expanded) {
            RemoteToolbarButton(label = "返回", alpha = alpha, onClick = onBack)
            Spacer(modifier = Modifier.height(8.dp))
            RemoteToolbarButton(label = "主页", alpha = alpha, onClick = onHome)
            Spacer(modifier = Modifier.height(8.dp))
            RemoteToolbarButton(label = "任务", alpha = alpha, onClick = onRecent)
            Spacer(modifier = Modifier.height(8.dp))
            RemoteToolbarButton(label = "退出", alpha = alpha, onClick = onExit)
            Spacer(modifier = Modifier.height(12.dp))
        }

        RemoteToolbarButton(
            label = if (expanded) "收起" else "菜单",
            alpha = alpha,
            onClick = onToggleExpanded,
        )
    }
}

@Composable
private fun RemoteToolbarButton(
    label: String,
    alpha: Float = 0.88f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
