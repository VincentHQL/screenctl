package com.scrctl.client.ui.file

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.ui.components.SegmentedControl
import com.scrctl.client.ui.components.BreadcrumbBar
import com.scrctl.client.ui.components.FileRow
import com.scrctl.client.ui.components.BottomActionBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    deviceId: Long,
    onBackClick: () -> Unit,
    viewModel: FileManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val transition = rememberInfiniteTransition(label = "fileManagerProgress")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "fileManagerProgressAlpha"
    )

    Scaffold(
        containerColor = background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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
                        IconButton(onClick = { viewModel.toggleSearchVisible() }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = primary
                            )
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                SegmentedControl(
                    options = listOf(StorageTab.Local, StorageTab.Remote),
                    selectedOption = uiState.selectedStorage,
                    onSelectionChange = { storage -> viewModel.selectStorage(storage) },
                    optionLabel = { storage ->
                        when (storage) {
                            StorageTab.Local -> "Local Storage"
                            StorageTab.Remote -> "Remote Device"
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )

                BreadcrumbBar(
                    path = uiState.breadcrumb,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )

                AnimatedVisibility(visible = uiState.searchVisible) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = onSurfaceVariant,
                            )
                        },
                        placeholder = { Text("搜索文件名") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = surface,
                            unfocusedContainerColor = surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 10.dp),
                    )
                }

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
                selectedCount = uiState.selectedCount,
                onUpload = { viewModel.uploadFile() },
                onDownload = { viewModel.downloadFiles() },
                onMove = { viewModel.moveFiles() },
                onDelete = { viewModel.deleteFiles() },
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
            items(uiState.visibleEntries, key = { it.id }) { entry ->
                FileRow(
                    entry = entry,
                    onToggle = { viewModel.toggleEntrySelection(entry.id) },
                    onOpen = { viewModel.openEntry(entry) },
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
