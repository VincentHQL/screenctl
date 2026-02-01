package com.scrctl.client.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrctl.client.core.database.model.Group
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Connection method types
enum class ConnectionMethod {
    DIRECT, WIRELESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAddScreen(
    viewModel: DeviceAddViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
    onAdded: () -> Unit = {}
) {
    val groups = viewModel.groups
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(groups) {
        if (selectedGroupId == null && groups.isNotEmpty()) {
            selectedGroupId = groups.first().id
        }
    }

    var selectedMethod by remember { mutableStateOf(ConnectionMethod.DIRECT) }
    var deviceName by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var adbPort by remember { mutableStateOf("5555") }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }

    LaunchedEffect(selectedMethod) {
        if (selectedMethod == ConnectionMethod.DIRECT) {
            // Direct mode doesn't need pairing fields
            pairingPort = ""
            pairingCode = ""
        }
    }

    var isConnecting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outline
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val ipErrorText by remember(selectedMethod, ipAddress) {
        derivedStateOf {
            when (selectedMethod) {
                ConnectionMethod.DIRECT, ConnectionMethod.WIRELESS -> {
                    if (ipAddress.trim().isEmpty()) "请输入 IP 地址"
                    else if (!isValidIpv4(ipAddress.trim())) "IP 地址不合法"
                    else null
                }
            }
        }
    }

    val adbPortErrorText by remember(selectedMethod, adbPort) {
        derivedStateOf {
            fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

            when (selectedMethod) {
                ConnectionMethod.DIRECT, ConnectionMethod.WIRELESS -> {
                    if (adbPort.trim().isEmpty()) "请输入 ADB 端口"
                    else if (parsePort(adbPort) == null) "ADB 端口不合法(1-65535)"
                    else null
                }
            }
        }
    }

    val pairingPortErrorText by remember(selectedMethod, pairingPort) {
        derivedStateOf {
            if (selectedMethod != ConnectionMethod.WIRELESS) return@derivedStateOf null
            fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            when {
                pairingPort.trim().isEmpty() -> "请输入配对端口"
                parsePort(pairingPort) == null -> "配对端口不合法(1-65535)"
                else -> null
            }
        }
    }

    val pairingErrorText by remember(selectedMethod, pairingCode) {
        derivedStateOf {
            if (selectedMethod != ConnectionMethod.WIRELESS) return@derivedStateOf null
            val trimmed = pairingCode.trim()
            when {
                trimmed.isEmpty() -> "请输入配对码"
                trimmed.any { !it.isDigit() } -> "配对码应为数字"
                else -> null
            }
        }
    }

    val isAddEnabled by remember(ipErrorText, adbPortErrorText, pairingPortErrorText, pairingErrorText, groups, selectedGroupId) {
        derivedStateOf {
            val hasGroup = (selectedGroupId != null) || groups.isNotEmpty()
            val baseOk = hasGroup && ipErrorText == null && adbPortErrorText == null
            if (selectedMethod == ConnectionMethod.WIRELESS) {
                baseOk && pairingPortErrorText == null && pairingErrorText == null
            } else {
                baseOk
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    scrolledContainerColor = backgroundColor
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回",
                            tint = onBackground
                        )
                    }
                },
                title = {
                    Text(
                        text = "添加设备",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = onBackground,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                actions = {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(0.dp)
                    ),
                color = backgroundColor.copy(alpha = 0.8f),
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        val groupId = selectedGroupId ?: groups.firstOrNull()?.id ?: return@Button

                        scope.launch {
                            if (isConnecting) return@launch
                            isConnecting = true
                            try {
                                val adbPortInt = adbPort.toIntOrNull() ?: 5555
                                val result = when (selectedMethod) {
                                    ConnectionMethod.WIRELESS -> {
                                        val pairingPortInt = pairingPort.toIntOrNull() ?: 0
                                        viewModel.connectAndAddDevice(
                                            groupId = groupId,
                                            method = selectedMethod,
                                            deviceName = deviceName,
                                            ipAddress = ipAddress,
                                            adbPort = adbPortInt,
                                            pairingPort = pairingPortInt,
                                            pairingCode = pairingCode,
                                        )
                                    }

                                    ConnectionMethod.DIRECT -> {
                                        viewModel.connectAndAddDevice(
                                            groupId = groupId,
                                            method = selectedMethod,
                                            deviceName = deviceName,
                                            ipAddress = ipAddress,
                                            adbPort = adbPortInt,
                                        )
                                    }
                                }

                                result.onSuccess {
                                    onAdded()
                                }.onFailure { t ->
                                    snackbarHostState.showSnackbar(t.message ?: "连接失败")
                                }
                            } finally {
                                isConnecting = false
                            }
                        }
                    },
                    enabled = isAddEnabled && !isConnecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "正在连接...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Smartphone,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "连接设备",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPrimary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(bottom = 80.dp)
        ) {
            // Group Selection Section
            GroupSelectionSection(
                groups = groups,
                selectedGroupId = selectedGroupId,
                onGroupChange = { selectedGroupId = it },
                primaryColor = primaryColor,
                textSecondaryColor = textSecondaryColor,
                surfaceColor = surfaceColor,
                borderColor = borderColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                InputField(
                    label = "设备名称",
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    placeholder = "例如 客厅电视 / Pixel 8",
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    primaryColor = primaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Method Section
            ConnectionMethodSection(
                selectedMethod = selectedMethod,
                onMethodChange = { selectedMethod = it },
                primaryColor = primaryColor,
                textSecondaryColor = textSecondaryColor,
                surfaceColor = surfaceColor,
                borderColor = borderColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Fields
            DynamicFieldsSection(
                selectedMethod = selectedMethod,
                ipAddress = ipAddress,
                onIpAddressChange = { ipAddress = it },
                adbPort = adbPort,
                onAdbPortChange = { adbPort = it },
                pairingPort = pairingPort,
                onPairingPortChange = { pairingPort = it },
                pairingCode = pairingCode,
                onPairingCodeChange = { pairingCode = it },
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                primaryColor = primaryColor,
                textSecondaryColor = textSecondaryColor
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GroupSelectionSection(
    groups: List<Group>,
    selectedGroupId: Long?,
    onGroupChange: (Long) -> Unit,
    primaryColor: Color,
    textSecondaryColor: Color,
    surfaceColor: Color,
    borderColor: Color
) {
    val onBackground = MaterialTheme.colorScheme.onBackground

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "所属分组",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp)
        )

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            var expanded by remember { mutableStateOf(false) }
            val selectedName = groups.firstOrNull { it.id == selectedGroupId }?.name
                ?: groups.firstOrNull()?.name
                ?: "暂无分组"

            OutlinedButton(
                onClick = {
                    if (groups.isNotEmpty()) expanded = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = surfaceColor,
                    contentColor = onBackground
                ),
                border = null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedName,
                        fontSize = 16.sp,
                        color = onBackground
                    )
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = group.name,
                                color = onBackground
                            )
                        },
                        onClick = {
                            onGroupChange(group.id)
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (selectedGroupId == group.id) primaryColor else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionMethodSection(
    selectedMethod: ConnectionMethod,
    onMethodChange: (ConnectionMethod) -> Unit,
    primaryColor: Color,
    textSecondaryColor: Color,
    surfaceColor: Color,
    borderColor: Color
) {
    val onBackground = MaterialTheme.colorScheme.onBackground

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "连接方式",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .background(surfaceColor, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ConnectionMethod.DIRECT to "本地直连",
                ConnectionMethod.WIRELESS to "无线调试",
            ).forEach { (method, label) ->
                val isSelected = selectedMethod == method
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) primaryColor else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else borderColor.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onMethodChange(method) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else textSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicFieldsSection(
    selectedMethod: ConnectionMethod,
    ipAddress: String,
    onIpAddressChange: (String) -> Unit,
    adbPort: String,
    onAdbPortChange: (String) -> Unit,
    pairingPort: String,
    onPairingPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    surfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textSecondaryColor: Color
) {
    when (selectedMethod) {
        ConnectionMethod.DIRECT -> {
            LocalDirectFieldsSection(
                ipAddress = ipAddress,
                onIpAddressChange = onIpAddressChange,
                adbPort = adbPort,
                onAdbPortChange = onAdbPortChange,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                primaryColor = primaryColor,
                textSecondaryColor = textSecondaryColor
            )
        }

        ConnectionMethod.WIRELESS -> {
            WirelessDebugFieldsSection(
                ipAddress = ipAddress,
                onIpAddressChange = onIpAddressChange,
                adbPort = adbPort,
                onAdbPortChange = onAdbPortChange,
                pairingPort = pairingPort,
                onPairingPortChange = onPairingPortChange,
                pairingCode = pairingCode,
                onPairingCodeChange = onPairingCodeChange,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                primaryColor = primaryColor,
                textSecondaryColor = textSecondaryColor
            )
        }
    }
}

@Composable
private fun LocalDirectFieldsSection(
    ipAddress: String,
    onIpAddressChange: (String) -> Unit,
    adbPort: String,
    onAdbPortChange: (String) -> Unit,
    surfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textSecondaryColor: Color
) {
    val onBackground = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SettingsEthernet,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "本地直连详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onBackground
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        InputField(
            label = "IP 地址",
            value = ipAddress,
            onValueChange = onIpAddressChange,
            placeholder = "例如 192.168.1.10",
            errorText = if (ipAddress.trim().isNotEmpty() && !isValidIpv4(ipAddress.trim())) "IP 地址不合法" else null,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            primaryColor = primaryColor,
            textSecondaryColor = textSecondaryColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        InputField(
            label = "端口",
            value = adbPort,
            onValueChange = onAdbPortChange,
            placeholder = "5555",
            keyboardType = KeyboardType.Number,
            errorText = if (adbPort.trim().isNotEmpty() && !isValidPort(adbPort.trim())) "ADB 端口不合法(1-65535)" else null,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            primaryColor = primaryColor,
            textSecondaryColor = textSecondaryColor
        )

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = primaryColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "请确保设备与当前电脑处于同一局域网内。",
                    fontSize = 13.sp,
                    color = textSecondaryColor,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WirelessDebugFieldsSection(
    ipAddress: String,
    onIpAddressChange: (String) -> Unit,
    adbPort: String,
    onAdbPortChange: (String) -> Unit,
    pairingPort: String,
    onPairingPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    surfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textSecondaryColor: Color
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val tipText = buildAnnotatedString {
        append("提示：在 Android 设备的")
        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
            append("「开发者选项」")
        }
        append(" > ")
        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
            append("「无线调试」")
        }
        append("中可以找到上述信息。请确保设备与电脑处于同一局域网。")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SettingsEthernet,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "无线调试详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onBackground
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                InputField(
                    label = "IP 地址",
                    value = ipAddress,
                    onValueChange = onIpAddressChange,
                    placeholder = "例如 192.168.1.10",
                    errorText = if (ipAddress.trim().isNotEmpty() && !isValidIpv4(ipAddress.trim())) "IP 地址不合法" else null,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    primaryColor = primaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                InputField(
                    label = "ADB 端口",
                    value = adbPort,
                    onValueChange = onAdbPortChange,
                    placeholder = "5555",
                    keyboardType = KeyboardType.Number,
                    errorText = if (adbPort.trim().isNotEmpty() && !isValidPort(adbPort.trim())) "ADB 端口不合法(1-65535)" else null,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    primaryColor = primaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                InputField(
                    label = "配对端口",
                    value = pairingPort,
                    onValueChange = onPairingPortChange,
                    placeholder = "5555",
                    keyboardType = KeyboardType.Number,
                    errorText = if (pairingPort.trim().isNotEmpty() && !isValidPort(pairingPort.trim())) "配对端口不合法(1-65535)" else null,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    primaryColor = primaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                InputField(
                    label = "配对码",
                    value = pairingCode,
                    onValueChange = onPairingCodeChange,
                    placeholder = "123456",
                    keyboardType = KeyboardType.Number,
                    errorText = if (pairingCode.trim().isNotEmpty() && pairingCode.trim().any { !it.isDigit() }) "配对码应为数字" else null,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    primaryColor = primaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = primaryColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tipText,
                    fontSize = 13.sp,
                    color = textSecondaryColor,
                    lineHeight = 18.sp
                )
            }
        }

        TextButton(
            onClick = { },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        ) {
            Text(
                text = "如何开启开发者选项？",
                fontSize = 12.sp,
                color = textSecondaryColor
            )
        }
    }
}

@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    errorText: String? = null,
    surfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textSecondaryColor: Color
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val isError = errorText != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = onBackground.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = textSecondaryColor
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = borderColor,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor,
                errorContainerColor = surfaceColor,
                focusedTextColor = onBackground,
                unfocusedTextColor = onBackground,
                focusedPlaceholderColor = textSecondaryColor,
                unfocusedPlaceholderColor = textSecondaryColor,
            ),
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 14.sp),
        )

        if (errorText != null) {
            Text(
                text = errorText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

private fun isValidPort(text: String): Boolean {
    val port = text.toIntOrNull() ?: return false
    return port in 1..65535
}

private fun isValidIpv4(text: String): Boolean {
    val s = text.trim()
    val parts = s.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        if (part.isEmpty()) return@all false
        if (part.length > 1 && part.startsWith('0')) {
            // 避免 01 这种形式
            return@all false
        }
        val n = part.toIntOrNull() ?: return@all false
        n in 0..255
    }
}

