package com.scrctl.client.ui.device.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val TermBg = Color(0xFF0D1117)
private val TermBar = Color(0xFF161B22)
private val TermPrompt = Color(0xFF3FB950)
private val TermOutput = Color(0xFFE6EDF3)
private val TermError = Color(0xFFF85149)
private val TermCommand = Color(0xFF79C0FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    deviceId: Long,
    onBackClick: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    LaunchedEffect(deviceId) {
        viewModel.setDeviceId(deviceId)
    }

    val lines = viewModel.lines
    val isRunning = viewModel.isRunning
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    Scaffold(
        containerColor = TermBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "终端",
                        color = TermPrompt,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回",
                            tint = TermOutput,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TermBar),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TermBar)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "$",
                    color = TermPrompt,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = TermOutput,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(TermPrompt),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isRunning && inputText.isNotBlank()) {
                                viewModel.runCommand(inputText)
                                inputText = ""
                            }
                        }
                    ),
                    singleLine = true,
                    enabled = !isRunning,
                    decorationBox = { inner ->
                        Box {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "输入命令…",
                                    color = TermOutput.copy(alpha = 0.35f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = TermPrompt,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.runCommand(inputText)
                                inputText = ""
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = TermPrompt,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(lines) { line ->
                Text(
                    text = line.text,
                    color = when (line.type) {
                        TerminalViewModel.LineType.COMMAND -> TermCommand
                        TerminalViewModel.LineType.OUTPUT -> TermOutput
                        TerminalViewModel.LineType.ERROR -> TermError
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}
