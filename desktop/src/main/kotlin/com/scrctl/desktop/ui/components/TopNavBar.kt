package com.scrctl.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrctl.desktop.model.NavSection
import com.scrctl.desktop.model.SystemStats

@Composable
fun TopNavBar(
	active: NavSection,
	onSelect: (NavSection) -> Unit,
	modifier: Modifier = Modifier,
) {
	val barBg = Color(0xFF111318)
	val border = Color(0xFF1F2937)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(56.dp)
			.background(barBg)
			.border(width = 1.dp, color = border),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Box(
				modifier = Modifier
					.size(28.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(MaterialTheme.colorScheme.primary),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = Icons.Filled.GridView,
					contentDescription = null,
					tint = Color.White,
					modifier = Modifier.size(18.dp),
				)
			}
			Text(
				text = "群控管理系统",
				color = Color.White,
				fontWeight = FontWeight.Bold,
				style = MaterialTheme.typography.labelLarge,
			)
		}

		Row(
			modifier = Modifier.weight(1f),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			NavPill(
				selected = active == NavSection.Devices,
				text = NavSection.Devices.title,
				icon = Icons.Filled.DevicesOther,
				onClick = { onSelect(NavSection.Devices) },
			)
			NavPill(
				selected = active == NavSection.Tasks,
				text = NavSection.Tasks.title,
				icon = Icons.Filled.TaskAlt,
				onClick = { onSelect(NavSection.Tasks) },
			)
			NavPill(
				selected = active == NavSection.Scripts,
				text = NavSection.Scripts.title,
				icon = Icons.Filled.Terminal,
				onClick = { onSelect(NavSection.Scripts) },
			)
			NavPill(
				selected = active == NavSection.Groups,
				text = NavSection.Groups.title,
				icon = Icons.Filled.GroupWork,
				onClick = { onSelect(NavSection.Groups) },
			)
			NavPill(
				selected = active == NavSection.Logs,
				text = NavSection.Logs.title,
				icon = Icons.Filled.HistoryEdu,
				onClick = { onSelect(NavSection.Logs) },
			)
			NavPill(
				selected = active == NavSection.Settings,
				text = NavSection.Settings.title,
				icon = Icons.Filled.Settings,
				onClick = { onSelect(NavSection.Settings) },
			)
		}
	}
}

@Composable
private fun NavPill(
	selected: Boolean,
	text: String,
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	onClick: () -> Unit,
) {
	val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
	val fg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
	val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
	Row(
		modifier = Modifier
			.clip(RoundedCornerShape(8.dp))
			.background(bg)
			.border(1.dp, border, RoundedCornerShape(8.dp))
			.clickable(
				indication = null,
				interactionSource = MutableInteractionSource(),
				onClick = onClick,
			)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
		Text(text = text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
	}
}


