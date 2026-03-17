package com.scrctl.client.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用的分段控件组件
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selectedOption: T,
    onSelectionChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val container = MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, outline.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
        color = container.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                SegmentedControlButton(
                    text = optionLabel(option),
                    selected = selectedOption == option,
                    onClick = { onSelectionChange(option) },
                    primary = primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SegmentedControlButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    primary: Color,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) primary.copy(alpha = 0.18f) else Color.Transparent
    val textColor = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.matchParentSize(),
        ) {}
        
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}