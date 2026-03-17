package com.scrctl.client.ui.remote

import android.content.pm.ActivityInfo
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.scrcpy.Scrcpy.SessionStage

data class RemoteScreenUiState(
    val uiStatus: UiStatus = UiStatus.IDLE,
    val statusText: String = "",
    val connectionSteps: List<ConnectionStepUi> = emptyList(),
    val currentStepIndex: Int = -1,
    val device: Device? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val displayOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    val connectedGamepadCount: Int = 0,
    val gamepadStatusText: String = "",
    val showConnectionSteps: Boolean = false,
    val isFirstFrameReady: Boolean = false,
    val remoteRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
)

data class ConnectionStepUi(
    val step: SessionStage,
    val title: String,
    val status: StepStatus,
    val detail: String,
)

data class StepTextSet(
    val title: String,
    val running: String = "",
    val success: String = "",
    val failed: String = "",
)

enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
}

enum class UiStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    STREAM_ERROR,
}