package com.scrctl.client.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    deviceId: Long,
    onExit: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    LaunchedEffect(deviceId) {
        viewModel.bind(deviceId)
    }

    val uiState by viewModel.uiState.collectAsState()
    var input by rememberSaveable(deviceId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chipScrollState = rememberScrollState()

    LaunchedEffect(uiState.entries.size, uiState.isExecuting) {
        if (uiState.entries.isNotEmpty()) {
            listState.animateScrollToItem(uiState.entries.lastIndex)
        }
    }

    val colors = MaterialTheme.colorScheme
    val terminalBackground = Color(0xFF091319)
    val terminalSurface = Color(0xFF102028)
    val terminalOutline = Color(0xFF1C3944)
    val commandTint = Color(0xFF8BFFB0)
    val systemTint = Color(0xFF6BCBFF)
    val errorTint = Color(0xFFFF8A80)

    fun submit() {
        val command = input.trim()
        if (command.isEmpty() || uiState.isExecuting) {
            return
        }
        viewModel.runCommand(command)
        input = ""
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "设备终端",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiState.deviceLabel.ifBlank { "设备 ID: $deviceId" },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearSession() }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "清空终端",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                modifier = Modifier.statusBarsPadding(),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TerminalStatusCard(
                isConnected = uiState.isConnected,
                isExecuting = uiState.isExecuting,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = terminalBackground,
                border = BorderStroke(1.dp, terminalOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(terminalSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TerminalDot(Color(0xFFFF5F57))
                        Spacer(modifier = Modifier.width(8.dp))
                        TerminalDot(Color(0xFFFEBB2E))
                        Spacer(modifier = Modifier.width(8.dp))
                        TerminalDot(Color(0xFF28C840))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "adb shell",
                            color = colors.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.entries, key = { it.id }) { line ->
                            TerminalLineItem(
                                line = line,
                                commandTint = commandTint,
                                systemTint = systemTint,
                                errorTint = errorTint,
                                defaultTint = colors.onSurface,
                            )
                        }

                        if (uiState.isExecuting) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.height(18.dp),
                                    )
                                    Text(
                                        text = "命令执行中...",
                                        color = systemTint,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (uiState.recentCommands.isNotEmpty()) "最近命令" else "常用命令",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val chips = remember(uiState.recentCommands, uiState.suggestedCommands) {
                        (uiState.recentCommands + uiState.suggestedCommands)
                            .distinct()
                            .take(8)
                    }
                    chips.forEach { command ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier.clickable { input = command },
                        ) {
                            Text(
                                text = command,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            text = if (uiState.isConnected) "输入 shell 命令，例如 getprop ro.product.model" else "设备离线，仅可查看历史记录",
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                    ),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = ::submit,
                    enabled = input.trim().isNotEmpty() && !uiState.isExecuting,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(text = if (uiState.isExecuting) "执行中" else "发送")
                }
            }

            if (!uiState.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.lastError.orEmpty(),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TerminalStatusCard(
    isConnected: Boolean,
    isExecuting: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isConnected) "设备在线" else "设备离线",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isExecuting) "当前有命令正在执行" else "每次发送独立执行一条 shell 命令",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isConnected) colors.primary.copy(alpha = 0.14f) else colors.error.copy(alpha = 0.14f),
            ) {
                Text(
                    text = if (isConnected) "READY" else "OFFLINE",
                    color = if (isConnected) colors.primary else colors.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TerminalDot(color: Color) {
    Box(
        modifier = Modifier
            .height(10.dp)
            .width(10.dp)
            .background(color = color, shape = CircleShape),
    )
}

@Composable
private fun TerminalLineItem(
    line: TerminalLine,
    commandTint: Color,
    systemTint: Color,
    errorTint: Color,
    defaultTint: Color,
) {
    val prefix = when (line.kind) {
        TerminalLineKind.Command -> "$"
        TerminalLineKind.Output -> ""
        TerminalLineKind.System -> "#"
        TerminalLineKind.Error -> "!"
    }
    val tint = when (line.kind) {
        TerminalLineKind.Command -> commandTint
        TerminalLineKind.Output -> defaultTint
        TerminalLineKind.System -> systemTint
        TerminalLineKind.Error -> errorTint
    }

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (prefix.isNotEmpty()) {
                Text(
                    text = prefix,
                    color = tint,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = line.text,
                color = tint,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp,
            )
        }
    }
}