package com.scrctl.client.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 显示设备实时画面。
 *
 * 内置可见性检测：只有当组件处于屏幕可见区域时才会轮询截屏，
 * 滚出可见区域后自动暂停，重新可见时自动恢复。
 */
@Composable
fun DeviceView(
	screencapProvider: suspend () -> Result<ByteArray>,
	modifier: Modifier = Modifier,
	refreshIntervalMs: Long = 500L,
) {
	var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
	var lastError by remember { mutableStateOf<String?>(null) }
	var loading by remember { mutableStateOf(true) }
	var isVisible by remember { mutableStateOf(false) }

	LaunchedEffect(screencapProvider, refreshIntervalMs, isVisible) {
		if (!isVisible) return@LaunchedEffect

		loading = true
		lastError = null

		while (isActive) {
			val result = screencapProvider()
			if (result.isSuccess) {
				imageBytes = result.getOrNull()
				loading = false
				lastError = null
			} else {
				loading = false
				lastError = result.exceptionOrNull()?.message ?: "获取画面失败"
			}
			delay(refreshIntervalMs)
		}
	}

	val bg = MaterialTheme.colorScheme.surfaceVariant
	Box(
		modifier = modifier
			.clip(RoundedCornerShape(16.dp))
			.background(bg)
			.onGloballyPositioned { coordinates ->
				isVisible = if (!coordinates.isAttached) {
					false
				} else {
					val bounds = coordinates.boundsInWindow()
					bounds.width > 0f && bounds.height > 0f
				}
			},
		contentAlignment = Alignment.Center,
	) {
		val bytes = imageBytes
		val bitmap = remember(bytes) {
			bytes?.let {
				BitmapFactory.decodeByteArray(it, 0, it.size)
			}
		}

		if (bitmap != null) {
			Image(
				bitmap = bitmap.asImageBitmap(),
				contentDescription = "device_screen",
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		}

		when {
			loading && bitmap == null -> {
				CircularProgressIndicator()
			}
			bitmap == null && lastError != null -> {
				Text(
					text = lastError ?: "未知错误",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(12.dp),
				)
			}
		}
	}
}

