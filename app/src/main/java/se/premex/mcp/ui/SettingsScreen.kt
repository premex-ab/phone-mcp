package se.premex.mcp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.premex.mcp.R
import se.premex.mcp.data.ServerConfig
import se.premex.mcp.remote.RemoteAccessViewModel

/**
 * Settings screen for configuring server host and port
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverConfig: ServerConfig,
    onNavigateBack: () -> Unit,
    onSaveSettings: (host: String, port: Int) -> Unit
) {
    var selectedHost by remember { mutableStateOf(serverConfig.host) }
    var portInput by remember { mutableStateOf(serverConfig.port.toString()) }
    var hostDropdownExpanded by remember { mutableStateOf(false) }

    // Derived state for validation
    val parsedPort = portInput.toIntOrNull()
    val isPortValid = parsedPort != null && parsedPort in 1..65535
    val showPortError = portInput.isNotEmpty() && !isPortValid

    // Predefined host options
    val hostOptions = listOf(
        "0.0.0.0" to stringResource(R.string.all_interfaces),
        "127.0.0.1" to stringResource(R.string.localhost_only)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Host selection section
            Text(
                text = stringResource(R.string.server_host),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hostDropdownExpanded = !hostDropdownExpanded },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.host),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = hostOptions.find { it.first == selectedHost }?.second ?: selectedHost,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(
                            imageVector = if (hostDropdownExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = stringResource(R.string.toggle_dropdown)
                        )
                    }
                }

                DropdownMenu(
                    expanded = hostDropdownExpanded,
                    onDismissRequest = { hostDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    hostOptions.forEach { (hostValue, hostLabel) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(hostLabel, fontWeight = FontWeight.Medium)
                                    Text(
                                        hostValue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedHost = hostValue
                                hostDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Port input section
            Text(
                text = stringResource(R.string.server_port),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = portInput,
                onValueChange = { newValue ->
                    // Only allow numeric input
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        portInput = newValue
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.port_number)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = showPortError,
                supportingText = {
                    val text = if (showPortError) {
                        stringResource(R.string.invalid_port_number_please_enter_a_value_between_1_and_65535)
                    } else {
                        stringResource(R.string.valid_port_range_1_65535)
                    }
                    Text(text)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    parsedPort?.let { port ->
                        if (port in 1..65535) {
                            onSaveSettings(selectedHost, port)
                            onNavigateBack()
                        }
                    }
                },
                enabled = isPortValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save_settings))
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            RemoteAccessSection()
        }
    }
}

@Composable
private fun RemoteAccessSection(viewModel: RemoteAccessViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.remote_access_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.remote_access_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = config.enabled,
                onCheckedChange = { viewModel.setEnabled(it) },
                enabled = !viewModel.busy
            )
        }

        if (viewModel.busy) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.remote_access_contacting_relay),
                style = MaterialTheme.typography.bodySmall
            )
        }

        viewModel.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (config.enabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.requestPairingCode() },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.show_pairing_code))
            }

            viewModel.pairingCode?.let { code ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pairing_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
