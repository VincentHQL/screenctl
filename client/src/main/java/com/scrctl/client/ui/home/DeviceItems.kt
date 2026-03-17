package com.scrctl.client.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.ui.components.StatusDot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val OFFLINE_STATUS_MAX_LEN = 12

// ── List row (single-column layout) ─────────────────────────────────────────────

@Composable
internal fun DeviceListRow(
    device: Device,
    isOnline: Boolean,
    errorText: String?,
    screencapProvider: (suspend (Long, Int, Int) -> Result<ByteArray>)?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val statusText = if (isOnline) "在线" else formatOfflineStatus(errorText)
    val statusColor = if (isOnline) primary else onSurfaceVariant.copy(alpha = 0.7f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar / tiny preview
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, primary.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (isOnline && screencapProvider != null) {
                    DeviceView(
						screencapProvider = { width, height -> screencapProvider(device.id, width, height) },
                        refreshIntervalMs = 5000L,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "IP: ${device.deviceAddr}",
                    fontSize = 10.sp,
                    color = primary.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(isOnline = isOnline, modifier = Modifier.size(8.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// ── Grid card (multi-column layout) ─────────────────────────────────────────────

@Composable
internal fun DeviceCard(
    device: Device,
    isOnline: Boolean,
    errorText: String?,
    screencapProvider: (suspend (Long, Int, Int) -> Result<ByteArray>)?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary

    val statusText = if (isOnline) "在线" else formatOfflineStatus(errorText)
    val statusColor = if (isOnline) primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
            ) {
                if (isOnline && screencapProvider != null) {
                    DeviceView(
						screencapProvider = { width, height -> screencapProvider(device.id, width, height) },
                        refreshIntervalMs = 1000L,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Placeholder gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        primary.copy(alpha = 0.22f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.08f)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = device.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusDot(isOnline = isOnline)
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "IP: ${device.deviceAddr}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                )
            }
        }
    }
}

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

private fun formatOfflineStatus(errorText: String?): String {
    val raw = errorText?.trim().takeUnless { it.isNullOrEmpty() } ?: return "离线"
    if (raw.length <= OFFLINE_STATUS_MAX_LEN) {
        return raw
    }
    return raw.take(OFFLINE_STATUS_MAX_LEN - 1) + "…"
}
