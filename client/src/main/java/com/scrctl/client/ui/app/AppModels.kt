package com.scrctl.client.ui.app

data class AppManagerUiState(
    val uiState: UiState = UiState.Loading,
    val detailsByPackage: Map<String, AppDetails> = emptyMap()
)

data class AppItem(
    val packageName: String,
    val apkPath: String?,
    val isSystem: Boolean,
)

data class AppDetails(
    val versionName: String?,
    val enabled: Boolean?,
    val isRunning: Boolean?,
    val apkPaths: List<String>?,
)

data class BottomInfo(
    val storageText: String,
    val memoryText: String,
)

sealed class UiState {
    data object Loading : UiState()
    data class Error(val message: String) : UiState()
    data class Ready(
        val apps: List<AppItem>,
        val bottomInfo: BottomInfo,
    ) : UiState()
}