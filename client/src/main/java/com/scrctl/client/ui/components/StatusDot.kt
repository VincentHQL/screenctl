package com.scrctl.client.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun StatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (isOnline) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    val transition = rememberInfiniteTransition(label = "statusDot")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "statusDotAlpha",
    )

    Box(
        modifier = Modifier
            .size(6.dp) // 默认大小
            .then(modifier)
            .clip(CircleShape)
            .background(baseColor.copy(alpha = if (isOnline) pulseAlpha else 0.85f)),
    )
}
