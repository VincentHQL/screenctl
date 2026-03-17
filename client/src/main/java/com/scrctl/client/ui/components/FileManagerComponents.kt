package com.scrctl.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrctl.client.ui.file.EntryKind
import com.scrctl.client.ui.file.FileEntry

/**
 * 面包屑导航栏
 */
@Composable
fun BreadcrumbBar(
    path: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = primary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = path,
            color = onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 文件行组件
 */
@Composable
fun FileRow(
    entry: FileEntry,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    primary: Color,
    surface: Color,
    outline: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = outline.copy(alpha = 0.55f)
    val bg = surface.copy(alpha = 0.80f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .clickable(onClick = onOpen),
        color = bg,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = entry.checked,
                onCheckedChange = { onToggle() },
            )

            val (icon, iconTint, iconBg) = when (entry.kind) {
                EntryKind.Folder -> Triple(Icons.Filled.Folder, primary, primary.copy(alpha = 0.14f))
                EntryKind.ImageFolder -> Triple(Icons.Filled.Image, primary, primary.copy(alpha = 0.14f))
                EntryKind.SharedFolder -> Triple(Icons.Filled.FolderShared, primary, primary.copy(alpha = 0.14f))
                EntryKind.Image -> Triple(Icons.Filled.Image, onSurfaceVariant, Color.Transparent)
                EntryKind.Video -> Triple(Icons.Filled.Movie, onSurfaceVariant, Color.Transparent)
                EntryKind.Doc -> Triple(Icons.Filled.Description, onSurfaceVariant, Color.Transparent)
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg)
                    .border(
                        width = if (iconBg == Color.Transparent) 1.dp else 0.dp,
                        color = outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    color = onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.meta,
                    color = onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.kind.isFolder) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = primary.copy(alpha = 0.35f)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = primary.copy(alpha = 0.35f)
                )
            }
        }
    }
}

/**
 * 底部操作栏
 */
@Composable
fun BottomActionBar(
    selectedCount: Int,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val container = MaterialTheme.colorScheme.surface
    val danger = MaterialTheme.colorScheme.error

    Column(modifier = modifier) {
        Surface(
            color = container.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, outline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = selectedCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(primary)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$selectedCount Items Selected",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ActionButton(
                        label = "Upload",
                        icon = Icons.Filled.Upload,
                        primary = true,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        containerColor = primary,
                        onClick = onUpload,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    ActionButton(
                        label = "Get",
                        icon = Icons.Filled.Download,
                        primary = false,
                        tint = primary,
                        containerColor = primary.copy(alpha = 0.10f),
                        onClick = onDownload,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    ActionButton(
                        label = "Move",
                        icon = Icons.AutoMirrored.Filled.DriveFileMove,
                        primary = false,
                        tint = primary,
                        containerColor = primary.copy(alpha = 0.10f),
                        onClick = onMove,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    ActionButton(
                        label = "Purge",
                        icon = Icons.Filled.DeleteForever,
                        primary = false,
                        tint = danger,
                        containerColor = danger.copy(alpha = 0.10f),
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(outline.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/**
 * 操作按钮
 */
@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}