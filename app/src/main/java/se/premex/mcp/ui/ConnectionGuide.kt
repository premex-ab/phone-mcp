package se.premex.mcp.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import se.premex.mcp.BuildConfig
import se.premex.mcp.R
import se.premex.mcp.remote.RemoteAccessConfig
import se.premex.mcp.remote.RemoteAccessViewModel
import se.premex.mcp.ui.theme.MCPServerTheme

private val REMOTE_SSE_URL = "${RemoteAccessConfig.RELAY_URL}/sse"

/** How the MCP client reaches this phone. Remote is the recommended default. */
private enum class ConnectionMode { REMOTE, LOCAL }

/**
 * Step-by-step guide for connecting an MCP client to this server.
 * Shown on the home screen while the server is running. Remote (through
 * phonemcp.ai) is the default mode; local Wi-Fi is the fallback for clients
 * on the same network.
 */
@Composable
fun ConnectionGuide(
    connectionUrl: String,
    authToken: String,
    modifier: Modifier = Modifier,
    isOnWifi: () -> Boolean = { true },
) {
    var mode by rememberSaveable { mutableStateOf(ConnectionMode.REMOTE) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.connect_your_ai_client),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Remote access is Play-exclusive (its subscription can only be sold
        // through Play Billing) — sideloaded builds are local-network only.
        if (!BuildConfig.REMOTE_ACCESS) {
            LocalGuide(connectionUrl, authToken, isOnWifi)
            return@Column
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == ConnectionMode.REMOTE,
                onClick = { mode = ConnectionMode.REMOTE },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.connection_mode_remote)) }
            SegmentedButton(
                selected = mode == ConnectionMode.LOCAL,
                onClick = { mode = ConnectionMode.LOCAL },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.connection_mode_local)) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (mode) {
            // hiltViewModel is unavailable when rendering @Preview compositions
            ConnectionMode.REMOTE ->
                if (LocalInspectionMode.current) LocalGuide(connectionUrl, authToken, isOnWifi)
                else RemoteGuide()

            ConnectionMode.LOCAL -> LocalGuide(connectionUrl, authToken, isOnWifi)
        }

    }
}

/** Remote mode: tunnel through phonemcp.ai, authorized with a pairing code. */
@Composable
private fun RemoteGuide(viewModel: RemoteAccessViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsState()
    val tunnelConnected by viewModel.tunnelConnected.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(config.enabled) {
        if (config.enabled) viewModel.refreshPairedClients()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.remote_access_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (config.enabled) {
            tunnelConnected?.let { connected ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (connected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (connected) R.string.remote_status_connected
                            else R.string.remote_status_connecting
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            viewModel.entitlement?.let { (status, activeUntil) ->
                // java.time needs API 26; minSdk is 24 — parse the ISO instant by hand
                val daysLeft = runCatching {
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val end = format.parse(activeUntil.substringBefore('.').removeSuffix("Z"))!!.time
                    ((end - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                }.getOrDefault(0L)
                val (text, color) = when (status) {
                    "trial" -> stringResource(R.string.remote_trial_days_left, daysLeft) to
                        MaterialTheme.colorScheme.onSurfaceVariant
                    "paid" -> stringResource(R.string.remote_subscription_active) to
                        MaterialTheme.colorScheme.tertiary
                    "grace" -> stringResource(R.string.remote_grace, daysLeft) to
                        MaterialTheme.colorScheme.error
                    else -> stringResource(R.string.remote_expired) to
                        MaterialTheme.colorScheme.error
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Subscribe: only when there is something to buy (Play answered)
                // and the device is not already covered by a paid subscription
                val product by viewModel.subscriptionProduct.collectAsState()
                if (status != "paid") {
                    product?.let { details ->
                        val offers = details.subscriptionOfferDetails.orEmpty()
                        fun pricing(basePlanId: String) = offers
                            .firstOrNull { it.basePlanId == basePlanId }
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()

                        val monthly = pricing("monthly")
                        val yearly = pricing("yearly")

                        if (monthly != null || yearly != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                monthly?.let { plan ->
                                    Button(
                                        onClick = {
                                            (context as? Activity)?.let { viewModel.subscribe(it, "monthly") }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.subscribe_monthly, plan.formattedPrice))
                                    }
                                }
                                yearly?.let { plan ->
                                    FilledTonalButton(
                                        onClick = {
                                            (context as? Activity)?.let { viewModel.subscribe(it, "yearly") }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.subscribe_yearly, plan.formattedPrice))
                                    }
                                }
                            }
                            if (monthly != null && yearly != null && monthly.priceAmountMicros > 0) {
                                val savings =
                                    (100 - yearly.priceAmountMicros * 100 / (monthly.priceAmountMicros * 12)).toInt()
                                if (savings > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.yearly_savings, savings),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // Base plans with unexpected ids — keep a generic button
                            Button(
                                onClick = { (context as? Activity)?.let { viewModel.subscribe(it) } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.subscribe))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Doze can pause the tunnel; ask for the battery exemption right
            // where the user just chose to be reachable from anywhere
            var ignoresBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        ignoresBattery = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            if (!ignoresBattery) {
                Text(
                    text = stringResource(R.string.battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + context.packageName)
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_button))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (!config.enabled) {
            Button(
                onClick = { viewModel.setEnabled(true) },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (viewModel.busy) R.string.remote_access_contacting_relay
                        else R.string.enable_remote_access
                    )
                )
            }
        } else {
            var selectedClient by rememberSaveable { mutableStateOf(McpClient.CLAUDE_CODE) }

            Text(
                text = stringResource(R.string.add_client_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            ClientChips(selectedClient) { selectedClient = it }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(selectedClient.remoteStep1),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            val snippet = selectedClient.remoteSnippet()
            CodeBlock(
                code = snippet,
                copyButtonText = stringResource(selectedClient.remoteCopyButtonText),
                onCopy = { copyToClipboard(context, snippet) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(selectedClient.remoteStep2),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.remote_step_3),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.paired_clients_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.refreshPairedClients() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh_paired_clients)
                    )
                }
            }

            if (viewModel.pairedClients.isEmpty()) {
                Text(
                    text = stringResource(R.string.paired_clients_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                viewModel.pairedClients.forEach { client ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = client.name ?: client.clientId,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.revokeClient(client.clientId) },
                            enabled = !viewModel.busy
                        ) {
                            Text(stringResource(R.string.revoke))
                        }
                    }
                }
            }
        }

        viewModel.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Local mode: direct connection over the same Wi-Fi network. */
@Composable
private fun LocalGuide(
    connectionUrl: String,
    authToken: String,
    isOnWifi: () -> Boolean = { true },
) {
    val context = LocalContext.current
    var selectedClient by rememberSaveable { mutableStateOf(McpClient.CLAUDE_CODE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        CopyableValue(
            label = stringResource(R.string.connection_url),
            value = connectionUrl,
            onCopy = { copyToClipboard(context, it) }
        )
        CopyableValue(
            label = stringResource(R.string.access_token),
            value = authToken,
            onCopy = { copyToClipboard(context, it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ClientChips(selectedClient) { selectedClient = it }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(selectedClient.instructions),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        val snippet = selectedClient.snippet(connectionUrl, authToken)
        CodeBlock(
            code = snippet,
            copyButtonText = stringResource(selectedClient.copyButtonText),
            onCopy = { copyToClipboard(context, snippet) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.wifi_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ConnectionDiagnostics(connectionUrl = connectionUrl, isOnWifi = isOnWifi)
    }
}

/** Local-only: ping the server's /health endpoint from this phone's Wi-Fi address. */
@Composable
private fun ConnectionDiagnostics(connectionUrl: String, isOnWifi: () -> Boolean) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testSucceeded by remember { mutableStateOf<Boolean?>(null) }

    val healthUrl = connectionUrl.removeSuffix("/sse") + "/health"

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isOnWifi()) {
            Text(
                text = stringResource(R.string.not_on_wifi_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = {
                testing = true
                testSucceeded = null
                scope.launch {
                    testSucceeded = withContext(Dispatchers.IO) {
                        try {
                            val connection =
                                URL(healthUrl).openConnection() as HttpURLConnection
                            connection.connectTimeout = 3000
                            connection.readTimeout = 3000
                            try {
                                connection.responseCode == HttpURLConnection.HTTP_OK
                            } finally {
                                connection.disconnect()
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    testing = false
                }
            },
            enabled = !testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (testing) stringResource(R.string.testing_connection)
                else stringResource(R.string.test_connection)
            )
        }

        testSucceeded?.let { succeeded ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (succeeded) {
                    stringResource(R.string.connection_test_success, healthUrl)
                } else {
                    stringResource(R.string.connection_test_failure, healthUrl)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (succeeded) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ClientChips(selected: McpClient, onSelect: (McpClient) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        McpClient.entries.forEach { client ->
            FilterChip(
                selected = selected == client,
                onClick = { onSelect(client) },
                label = { Text(stringResource(client.title)) },
                leadingIcon = if (selected == client) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
    }
}

/** The MCP clients the guide can generate ready-to-paste setup snippets for. */
private enum class McpClient(
    @StringRes val title: Int,
    @StringRes val instructions: Int,
    @StringRes val copyButtonText: Int,
    @StringRes val remoteStep1: Int,
    @StringRes val remoteStep2: Int,
    @StringRes val remoteCopyButtonText: Int,
) {
    CLAUDE_CODE(
        title = R.string.client_claude_code,
        instructions = R.string.claude_code_instructions,
        copyButtonText = R.string.copy_command,
        remoteStep1 = R.string.remote_step_code_1,
        remoteStep2 = R.string.remote_step_code_2,
        remoteCopyButtonText = R.string.copy_command,
    ) {
        override fun snippet(url: String, token: String): String =
            "claude mcp add --transport sse phone $url " +
                "--header \"Authorization: Bearer $token\""

        override fun remoteSnippet(): String =
            "claude mcp add --transport sse phone $REMOTE_SSE_URL"
    },
    CLAUDE_DESKTOP(
        title = R.string.client_claude_desktop,
        instructions = R.string.claude_desktop_instructions,
        copyButtonText = R.string.copy_configuration,
        remoteStep1 = R.string.remote_step_desktop_1,
        remoteStep2 = R.string.remote_step_desktop_2,
        remoteCopyButtonText = R.string.copy_url,
    ) {
        override fun snippet(url: String, token: String): String = """
            {
              "mcpServers": {
                "phone": {
                  "command": "npx",
                  "args": [
                    "mcp-remote",
                    "$url",
                    "--header",
                    "Authorization: Bearer $token",
                    "--allow-http"
                  ]
                }
              }
            }
        """.trimIndent()

        override fun remoteSnippet(): String = REMOTE_SSE_URL
    },
    OTHER(
        title = R.string.client_other,
        instructions = R.string.other_client_instructions,
        copyButtonText = R.string.copy_details,
        remoteStep1 = R.string.remote_step_other_1,
        remoteStep2 = R.string.remote_step_other_2,
        remoteCopyButtonText = R.string.copy_details,
    ) {
        override fun snippet(url: String, token: String): String =
            "URL: $url\nHeader: Authorization: Bearer $token\nTransport: SSE over HTTP"

        override fun remoteSnippet(): String =
            "URL: $REMOTE_SSE_URL\n" +
                "Auth: OAuth 2.1 (authorization code + PKCE, dynamic registration)\n" +
                "Transport: SSE over HTTPS"
    };

    abstract fun snippet(url: String, token: String): String
    abstract fun remoteSnippet(): String
}

@Composable
private fun CopyableValue(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            maxLines = 1
        )
        IconButton(onClick = { onCopy(value) }) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.copy, label)
            )
        }
    }
}

@Composable
private fun CodeBlock(
    code: String,
    copyButtonText: String,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
            TextButton(
                onClick = onCopy,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(copyButtonText)
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)

private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("MCP server", text))
    // Android 13+ shows its own clipboard confirmation overlay
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionGuidePreview() {
    MCPServerTheme {
        ConnectionGuide(
            connectionUrl = "http://192.168.1.42:3001/sse",
            authToken = "123456"
        )
    }
}
