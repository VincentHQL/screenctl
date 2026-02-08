package com.scrctl.client.ui.device.remote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    deviceId: String,
    onExit: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Background blurred preview grid
        BlurredPreviewGrid(
            tileColor = surfaceVariant,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.20f, scaleX = 1.10f, scaleY = 1.10f)
                .blur(22.dp)
        )

        // Subtle tech dot pattern overlay (hex-bg like)
        DotPatternOverlay(
            dotColor = primary.copy(alpha = 0.06f),
            stepDp = 24,
            modifier = Modifier.fillMaxSize(),
        )

        // Back button (kept for usability)
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "返回",
                tint = onBackground,
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TechConnectingAnimation(
                primary = primary,
                outline = outline,
                surfaceVariant = surfaceVariant,
            )

            Spacer(modifier = Modifier.size(18.dp))

            Text(
                text = "正在连接到设备",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "正在为您准备流畅的操控体验，请稍后...",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            Spacer(modifier = Modifier.size(22.dp))
            Text(
                text = "设备：$deviceId",
                fontSize = 12.sp,
                color = onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BlurredPreviewGrid(
    tileColor: Color,
    modifier: Modifier = Modifier,
) {
    val tiles = remember {
        List(4) { it }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            items(tiles, key = { it }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tileColor)
                )
            }
        }
    )
}

@Composable
private fun DotPatternOverlay(
    dotColor: Color,
    stepDp: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stepPx = stepDp.dp.toPx()
        val r = 1.2.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(color = dotColor, radius = r, center = androidx.compose.ui.geometry.Offset(x + r, y + r))
                x += stepPx
            }
            y += stepPx
        }
    }
}

@Composable
private fun TechConnectingAnimation(
    primary: Color,
    outline: Color,
    surfaceVariant: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "connectingTech")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1100), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1100), repeatMode = RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier.size(192.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer ring (pulsing)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = primary.copy(alpha = pulseAlpha),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // Inner ring (steady)
        Canvas(modifier = Modifier.size(144.dp)) {
            drawCircle(
                color = primary.copy(alpha = 0.35f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // Center “holographic hex” (rotated square like prototype)
        Surface(
            color = primary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    rotationZ = 45f
                }
                .border(
                    width = 1.dp,
                    color = primary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = primary.copy(alpha = 0.95f),
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { rotationZ = -45f }
                )

                // Subtle glow
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(primary.copy(alpha = glowAlpha * 0.10f))
                )
            }
        }
    }
}
