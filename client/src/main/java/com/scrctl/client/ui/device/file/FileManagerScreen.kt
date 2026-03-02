package com.scrctl.client.ui.device.file

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    deviceId: Long,
    onBackClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var selectedStorage by remember { mutableStateOf(StorageTab.Remote) }
    var breadcrumb by remember { mutableStateOf("Root / Internal Storage / Downloads") }

    val entries = remember {
        mutableStateListOf(
            FileEntry(
                id = "folder_system",
                kind = EntryKind.Folder,
                name = "System_Config",
                meta = "May 12, 2024 • 8 items",
            ),
            FileEntry(
                id = "folder_dcim",
                kind = EntryKind.ImageFolder,
                name = "DCIM_Backup",
                meta = "Today, 10:45 AM • 142 items",
                checked = true,
            ),
            FileEntry(
                id = "file_img",
                kind = EntryKind.Image,
                name = "vacation_photo_01.jpg",
                meta = "2.4 MB • JPG",
                checked = true,
            ),
            FileEntry(
                id = "file_video",
                kind = EntryKind.Video,
                name = "production_demo.mp4",
                meta = "45.8 MB • MP4",
            ),
            FileEntry(
                id = "file_doc",
                kind = EntryKind.Doc,
                name = "security_log_v2.txt",
                meta = "14 KB • TEXT",
                checked = true,
            ),
            FileEntry(
                id = "folder_shared",
                kind = EntryKind.SharedFolder,
                name = "Shared_Project",
                meta = "Yesterday • 12 files",
            ),
        )
    }

    val selectedCount = entries.count { it.checked }
    val transition = rememberInfiniteTransition(label = "fileManagerProgress")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "fileManagerProgressAlpha"
    )

    Scaffold(
        containerColor = background,
        topBar = {
            Column(modifier = Modifier.background(background)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "REMOTE MANAGER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = primary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO: search */ }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = primary
                            )
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                StorageSegmentedControl(
                    selected = selectedStorage,
                    onSelect = { selectedStorage = it },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )

                BreadcrumbBar(
                    path = breadcrumb,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )

                // 顶部极细进度条（原型的 floating mini-bar）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.33f)
                            .background(primary.copy(alpha = pulseAlpha))
                    )
                }
            }
        },
        bottomBar = {
            BottomActionBar(
                selectedCount = selectedCount,
                onUpload = { /* TODO */ },
                onDownload = { /* TODO */ },
                onMove = { /* TODO */ },
                onDelete = { /* TODO */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 120.dp, top = 12.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                FileRow(
                    entry = entry,
                    onToggle = {
                        val idx = entries.indexOfFirst { it.id == entry.id }
                        if (idx >= 0) entries[idx] = entries[idx].copy(checked = !entries[idx].checked)
                    },
                    onOpen = {
                        // 仅做 UI 演示：打开文件夹时更新面包屑
                        if (entry.kind.isFolder) {
                            breadcrumb = "Root / Internal Storage / ${entry.name}"
                        }
                    },
                    primary = primary,
                    surface = surface,
                    outline = outline,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                )
            }
        }
    }
}

private enum class StorageTab { Local, Remote }

private enum class EntryKind(val isFolder: Boolean) {
    Folder(true),
    ImageFolder(true),
    SharedFolder(true),
    Image(false),
    Video(false),
    Doc(false),
}

private data class FileEntry(
    val id: String,
    val kind: EntryKind,
    val name: String,
    val meta: String,
    val checked: Boolean = false,
)

@Composable
private fun StorageSegmentedControl(
    selected: StorageTab,
    onSelect: (StorageTab) -> Unit,
    modifier: Modifier = Modifier,
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
            SegButton(
                text = "Local Storage",
                selected = selected == StorageTab.Local,
                onClick = { onSelect(StorageTab.Local) },
                primary = primary,
                modifier = Modifier.weight(1f)
            )
            SegButton(
                text = "Remote Device",
                selected = selected == StorageTab.Remote,
                onClick = { onSelect(StorageTab.Remote) },
                primary = primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    primary: Color,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) primary.copy(alpha = 0.18f) else Color.Transparent
    val fg = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BreadcrumbBar(
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

@Composable
private fun FileRow(
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
            androidx.compose.material3.Checkbox(
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

@Composable
private fun BottomActionBar(
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

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
