package com.scrctl.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryColor = Color(0xFF135BEC)

private val DarkBackgroundColor = Color(0xFF0A0F18)
private val DarkSurfaceColor = Color(0xFF0D121C)
private val DarkPanelColor = Color(0xFF111827)

private val LightBackgroundColor = Color(0xFFF6F6F8)
private val LightSurfaceColor = Color(0xFFFFFFFF)

val ScrctlDarkColorScheme: ColorScheme = darkColorScheme(
	primary = PrimaryColor,
	background = DarkBackgroundColor,
	surface = DarkSurfaceColor,
	onPrimary = Color.White,
	onBackground = Color(0xFFE5E7EB),
	onSurface = Color(0xFFE5E7EB),
)

val ScrctlLightColorScheme: ColorScheme = lightColorScheme(
	primary = PrimaryColor,
	background = LightBackgroundColor,
	surface = LightSurfaceColor,
	onPrimary = Color.White,
	onBackground = Color(0xFF0F172A),
	onSurface = Color(0xFF0F172A),
)

object ScrctlColors {
	val Primary: Color = PrimaryColor
	val DarkBackground: Color = DarkBackgroundColor
	val DarkSurface: Color = DarkSurfaceColor
	val DarkPanel: Color = DarkPanelColor
}

@Composable
fun ScrctlTheme(
	dark: Boolean,
	content: @Composable () -> Unit,
) {
	MaterialTheme(
		colorScheme = if (dark) ScrctlDarkColorScheme else ScrctlLightColorScheme,
		content = content,
	)
}
