package se.premex.mcp.ui

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.premex.mcp.AddressFamily
import se.premex.mcp.NetworkAddress
import se.premex.mcp.NetworkUtils
import se.premex.mcp.R
import se.premex.mcp.data.ServerConfig

private data class HostOption(
    val host: String,
    val label: String,
    val detail: String
)

@Composable
private fun rememberBindableNetworkAddresses(): List<NetworkAddress> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf(NetworkUtils.getBindableNetworkAddresses()) }

    DisposableEffect(context, coroutineScope) {
        val connectivityManager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)
        val networkRequest = NetworkRequest.Builder().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                clearCapabilities()
            } else {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }
        }.build()
        var isRegistered = false

        fun refreshAfter(delayMillis: Long = 0) {
            coroutineScope.launch {
                if (delayMillis > 0) delay(delayMillis)
                addresses = NetworkUtils.getBindableNetworkAddresses()
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Link properties normally follow immediately; the delay also covers API 24–25.
                refreshAfter(100)
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                refreshAfter()
            }

            override fun onLost(network: Network) {
                // Allow NetworkInterface to reflect the removal before rescanning.
                refreshAfter(100)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, callback)
            isRegistered = true
        } catch (_: Exception) {
            // Keep the initial snapshot if callbacks are unavailable on this device.
        }

        onDispose {
            if (isRegistered) {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                    // The callback may already have been removed by the system.
                }
            }
        }
    }

    return addresses
}

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
    var selectedHost by remember(serverConfig.host) { mutableStateOf(serverConfig.host) }
    var portInput by remember(serverConfig.port) { mutableStateOf(serverConfig.port.toString()) }
    var hostDropdownExpanded by remember { mutableStateOf(false) }

    // Derived state for validation
    val parsedPort = portInput.toIntOrNull()
    val isPortValid = parsedPort != null && parsedPort in 1..65535
    val showPortError = portInput.isNotEmpty() && !isPortValid

    val networkAddresses = rememberBindableNetworkAddresses()
    val hostOptions = buildList {
        add(
            HostOption(
                host = "0.0.0.0",
                label = stringResource(R.string.all_interfaces),
                detail = "0.0.0.0"
            )
        )
        add(
            HostOption(
                host = "127.0.0.1",
                label = stringResource(R.string.localhost_only),
                detail = "127.0.0.1"
            )
        )
        networkAddresses.forEach { networkAddress ->
            val family = when (networkAddress.addressFamily) {
                AddressFamily.IPV4 -> stringResource(R.string.ipv4)
                AddressFamily.IPV6 -> stringResource(R.string.ipv6)
            }
            add(
                HostOption(
                    host = networkAddress.address,
                    label = networkAddress.interfaceLabel,
                    detail = "${networkAddress.address} • $family"
                )
            )
        }

        if (none { it.host == selectedHost }) {
            add(
                HostOption(
                    host = selectedHost,
                    label = stringResource(R.string.saved_interface_unavailable),
                    detail = selectedHost
                )
            )
        }
    }
    val selectedOption = hostOptions.find { it.host == selectedHost }

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
                                text = selectedOption?.label ?: selectedHost,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            selectedOption?.let { option ->
                                Text(
                                    text = option.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                    hostOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        option.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedHost = option.host
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

        }
    }
}
