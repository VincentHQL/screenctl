package com.scrctl.client.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group

// ── Drawer sheet ────────────────────────────────────────────────────────────────

@Composable
internal fun GroupDrawerContent(
    groups: List<Group>,
    selectedGroupId: Long?,
    allDevices: List<Device>,
    onGroupSelect: (Long?) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (Group) -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.width(288.dp)) {
        val primary = MaterialTheme.colorScheme.primary
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
        val outlineVariant = MaterialTheme.colorScheme.outlineVariant

        var isCreatingGroup by remember { mutableStateOf(false) }
        var newGroupName by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(24.dp)
                .padding(top = 24.dp),
        ) {
            // Header
            DrawerHeader(primary = primary, onSurface = onSurface)

            // Group list
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GroupItem(
                    icon = Icons.Filled.Devices,
                    label = "所有设备",
                    count = allDevices.size,
                    isSelected = selectedGroupId == null,
                    onClick = { onGroupSelect(null) },
                    onDeleteClick = null,
                )

                groups.forEach { group ->
                    val count = allDevices.count { it.groupId == group.id }
                    GroupItem(
                        icon = Icons.Filled.AccountTree,
                        label = group.name,
                        count = count,
                        isSelected = selectedGroupId == group.id,
                        onClick = { onGroupSelect(group.id) },
                        onDeleteClick = { onDeleteGroup(group) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                CreateGroupSection(
                    isCreating = isCreatingGroup,
                    newName = newGroupName,
                    onNewNameChange = { newGroupName = it },
                    onToggleCreating = { isCreatingGroup = it },
                    outlineVariant = outlineVariant,
                    onSurfaceVariant = onSurfaceVariant,
                    onConfirm = { name ->
                        onCreateGroup(name)
                        newGroupName = ""
                        isCreatingGroup = false
                    },
                )
            }
        }
    }
}

// ── Drawer sub-components ───────────────────────────────────────────────────────

@Composable
private fun DrawerHeader(primary: Color, onSurface: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = null,
                tint = primary,
            )
        }
        Text(
            text = "分组管理",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface,
        )
    }
}

@Composable
private fun CreateGroupSection(
    isCreating: Boolean,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onToggleCreating: (Boolean) -> Unit,
    outlineVariant: Color,
    onSurfaceVariant: Color,
    onConfirm: (String) -> Unit,
) {
    OutlinedButton(
        onClick = { onToggleCreating(true) },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = onSurfaceVariant,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, outlineVariant.copy(alpha = 0.7f)),
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "新建分组", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }

    if (isCreating) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = newName,
            onValueChange = onNewNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("请输入分组名称") },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val trimmed = newName.trim()
                if (trimmed.isNotEmpty()) onConfirm(trimmed)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("确认")
        }
    }
}

// ── Group item ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupItem(
    icon: ImageVector,
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val backgroundColor = if (isSelected) primary.copy(alpha = 0.1f) else Color.Transparent
    val textColor = if (isSelected) primary else onSurfaceVariant
    val badgeColor = if (isSelected) primary.copy(alpha = 0.2f) else surfaceVariant.copy(alpha = 0.7f)

    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (onDeleteClick != null) showMenu = true },
                ),
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                    Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(text = count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("删除分组") },
                onClick = {
                    showMenu = false
                    onDeleteClick?.invoke()
                },
            )
        }
    }
}
