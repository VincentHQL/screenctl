package com.scrctl.client.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ── Search ──────────────────────────────────────────────────────────────────────

@Composable
internal fun DeviceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = {
            Text(
                text = "搜索设备或 ID...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ── Section header with view toggle ─────────────────────────────────────────────

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String,
    gridColumns: Int,
    onGridColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        ViewToggle(
            selectedColumns = gridColumns.coerceIn(1, 3),
            onSelect = onGridColumnsChange,
        )
    }
}

// ── View toggle (list / grid 2 / grid 3) ────────────────────────────────────────

@Composable
private fun ViewToggle(
    selectedColumns: Int,
    onSelect: (Int) -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ViewToggleButton(
            selected = selectedColumns == 1,
            onClick = { onSelect(1) },
            icon = Icons.AutoMirrored.Filled.ViewList,
            selectedContainerColor = selectedColor,
        )
        ViewToggleButton(
            selected = selectedColumns == 2,
            onClick = { onSelect(2) },
            icon = Icons.Filled.GridView,
            selectedContainerColor = selectedColor,
        )
        ViewToggleButton(
            selected = selectedColumns == 3,
            onClick = { onSelect(3) },
            icon = Icons.Filled.ViewModule,
            selectedContainerColor = selectedColor,
        )
    }
}

@Composable
private fun ViewToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedContainerColor: Color,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val bg = if (selected) selectedContainerColor else Color.Transparent

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
