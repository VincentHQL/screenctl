package com.scrctl.client.ui.components

import android.graphics.BitmapFactory
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
	screencapProvider: suspend (width: Int, height: Int) -> Result<ByteArray>,
	modifier: Modifier = Modifier,
	refreshIntervalMs: Long = 500L,
) {
	var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
	var lastError by remember { mutableStateOf<String?>(null) }
	var loading by remember { mutableStateOf(true) }
	var isVisible by remember { mutableStateOf(false) }
	var viewSize by remember { mutableStateOf(IntSize.Zero) }

	LaunchedEffect(screencapProvider, refreshIntervalMs, isVisible, viewSize) {
		if (!isVisible) return@LaunchedEffect
		if (viewSize.width <= 0 || viewSize.height <= 0) return@LaunchedEffect

		loading = true
		lastError = null

		while (isActive) {
			val result = screencapProvider(viewSize.width, viewSize.height)
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
				viewSize = coordinates.size
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

/**
 * 使用 [SurfaceView] 渲染视频画面（例如 H.264 硬解输出）。
 *
 * 该 composable 只负责提供 [Surface]，实际的解码、渲染由调用方控制：
 * - [onSurfaceReady] 时，将 Surface 交给 MediaCodec / 渲染管线
 * - [onSurfaceDestroyed] 时，停止解码并释放资源
 */
@Composable
fun DeviceSurfaceView(
	modifier: Modifier = Modifier,
	onSurfaceReady: (Surface) -> Unit,
	onSurfaceDestroyed: (() -> Unit)? = null,
) {
	var isVisible by remember { mutableStateOf(false) }
	var currentSurface by remember { mutableStateOf<Surface?>(null) }
	var surfaceNotifiedReady by remember { mutableStateOf(false) }
	val onSurfaceReadyState = rememberUpdatedState(onSurfaceReady)
	val onSurfaceDestroyedState = rememberUpdatedState(onSurfaceDestroyed)

	LaunchedEffect(isVisible, currentSurface) {
		val surface = currentSurface
		if (isVisible && surface != null && !surfaceNotifiedReady) {
			onSurfaceReadyState.value.invoke(surface)
			surfaceNotifiedReady = true
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
		AndroidView(
			modifier = Modifier.fillMaxSize(),
			factory = { context ->
				SurfaceView(context).apply {
					holder.addCallback(object : SurfaceHolder.Callback {
						override fun surfaceCreated(holder: SurfaceHolder) {
							currentSurface = holder.surface
							surfaceNotifiedReady = false
							if (isVisible) {
								onSurfaceReadyState.value.invoke(holder.surface)
								surfaceNotifiedReady = true
							}
						}

						override fun surfaceChanged(
							holder: SurfaceHolder,
							format: Int,
							width: Int,
							height: Int,
						) {
							// 如需根据尺寸调整解码器，可在外部自行处理
						}

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

