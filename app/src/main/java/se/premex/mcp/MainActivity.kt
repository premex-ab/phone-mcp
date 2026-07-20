package se.premex.mcp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.AndroidEntryPoint
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import se.premex.mcp.auth.AuthRepository
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.data.ServerPreferencesRepository
import se.premex.mcp.di.ToolService
import se.premex.mcp.ui.SettingsScreen
import se.premex.mcp.ui.theme.MCPServerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Add dialog state for tool warnings
    private var showToolWarningDialog = mutableStateOf(false)
    private var currentToolRequiringWarning: McpTool? = null

    @Inject
    lateinit var toolService: ToolService

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var serverPreferencesRepository: ServerPreferencesRepository

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.all { it.value }
        if (allGranted) {
            toggleService(true)
        } else {
            // Some permissions denied - inform the user
            Toast.makeText(
                this,
                getString(R.string.all_permissions_are_required_to_run_the_mcp_server_service),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Keep this for backward compatibility or single permission scenarios
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleService(true)
        } else {
            // Permission denied - inform the user that the service cannot be started
            Toast.makeText(
                this,
                getString(R.string.permission_is_required_to_run_the_mcp_server_service),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val toolStates by toolService.toolEnabledStates.collectAsState()
            val serverConfig by serverPreferencesRepository.getServerConfig().collectAsState(
                initial = se.premex.mcp.data.ServerConfig()
            )

            // Extract the auth token from the instructions string
            // The format is "Please use the token 'XXXXXX' to authenticate your connection."
            val authInstructions = authRepository.getConnectionInstructions()
            val authToken = authInstructions.substringAfter("'").substringBefore("'")

            MCPServerTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            isRunning = McpServerService.isRunning.value,
                            onToggleServer = { shouldStart ->
                                // Check permissions only when trying to start the service
                                if (shouldStart) {
                                    checkRequiredPermissions()
                                } else {
                                    // No permission needed to stop the service
                                    toggleService(false)
                                }
                            },
                            getConnectionUrl = { getConnectionUrl(serverConfig.port) },
                            isOnWifi = { NetworkUtils.getWifiIpAddress(this@MainActivity) != null },
                            tools = toolService.tools.toList(),
                            toolEnabledStates = toolStates,
                            onToggleTool = { tool ->
                                handleToolToggle(tool)
                            },
                            authToken = authToken
                        )

                        // Show warning dialog if needed
                        if (showToolWarningDialog.value && currentToolRequiringWarning != null) {
                            ToolWarningDialog(
                                tool = currentToolRequiringWarning!!,
                                onDismiss = {
                                    // Cancel enabling the tool
                                    showToolWarningDialog.value = false
                                    currentToolRequiringWarning = null
                                },
                                onConfirm = {
                                    // User confirmed, enable the tool
                                    showToolWarningDialog.value = false
                                    currentToolRequiringWarning?.let { tool ->
                                        toolService.toggleToolEnabled(tool.id)
                                    }
                                    currentToolRequiringWarning = null
                                }
                            )
                        }
                    }

                    composable("settings") {
                        SettingsScreen(
                            serverConfig = serverConfig,
                            onNavigateBack = { navController.popBackStack() },
                            onSaveSettings = { host: String, port: Int ->
                                serverPreferencesRepository.updateServerConfig(host, port)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkRequiredPermissions() {
        // Create a set to store all required permissions
        val requiredPermissions = mutableSetOf<String>()

        // Add notification permission if on Android 13 (Tiramisu) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Get permissions from each enabled tool
        toolService.tools.forEach { tool ->
            if (toolService.toolEnabledStates.value[tool.id] == true) {
                // Add all permissions required by this enabled tool
                requiredPermissions.addAll(tool.requiredPermissions())
            }
        }

        // If no permissions required, start service directly
        if (requiredPermissions.isEmpty()) {
            toggleService(true)
            return
        }

        // Check if all permissions are already granted
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        when {
            // All permissions already granted
            missingPermissions.isEmpty() -> {
                toggleService(true)
            }
            // Request the missing permissions
            else -> {
                if (missingPermissions.size == 1) {
                    // If only one permission is needed, use the single permission request
                    requestPermissionLauncher.launch(missingPermissions.first())
                } else {
                    // If multiple permissions are needed, use the multiple permissions request
                    requestMultiplePermissionsLauncher.launch(missingPermissions)
                }
            }
        }
    }

    private fun toggleService(start: Boolean) {
        val serviceIntent = Intent(this, McpServerService::class.java)

        if (start) {
            // Start service (no need to pass tool states anymore, they're loaded from DataStore)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
    }

    private fun getConnectionUrl(port: Int): String {
        val ipAddress = NetworkUtils.getWifiIpAddress(this) ?: "0.0.0.0"
        return "http://$ipAddress:$port/sse"
    }

    // Function to handle tool toggle with warning dialog if needed
    private fun handleToolToggle(tool: McpTool) {
        // If the tool is already enabled, just disable it without warning
        if (toolService.toolEnabledStates.value[tool.id] == true) {
            toolService.toggleToolEnabled(tool.id)
            return
        }

        // Check if the tool has a warning message (disclaim property)
        if (tool.disclaim != null) {
            // Show warning dialog for this tool
            currentToolRequiringWarning = tool
            showToolWarningDialog.value = true
        } else {
            // No warning needed, just enable the tool
            toolService.toggleToolEnabled(tool.id)
        }
    }

    // Composable function for the warning dialog
    @Composable
    private fun ToolWarningDialog(
        tool: McpTool,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.warning_for, tool.name)) },
            text = { Text(tool.disclaim ?: stringResource(R.string.no_description_available)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    isRunning: Boolean,
    onToggleServer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    getConnectionUrl: () -> String,
    tools: List<McpTool>,
    toolEnabledStates: Map<String, Boolean>,
    onToggleTool: (McpTool) -> Unit,
    authToken: String = "YTpi",
    isOnWifi: () -> Boolean = { true }
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mcp_server)) },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        McpServerControl(
            isRunning = isRunning,
            onToggleServer = onToggleServer,
            modifier = modifier.padding(innerPadding),
            getConnectionUrl = getConnectionUrl,
            tools = tools,
            toolEnabledStates = toolEnabledStates,
            onToggleTool = onToggleTool,
            authToken = authToken,
            isOnWifi = isOnWifi
        )
    }
}

@Composable
fun McpServerControl(
    isRunning: Boolean,
    onToggleServer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    getConnectionUrl: () -> String,
    tools: List<McpTool>,
    toolEnabledStates: Map<String, Boolean>,
    onToggleTool: (McpTool) -> Unit,
    authToken: String = "YTpi",
    isOnWifi: () -> Boolean = { true }
) {
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 16.dp + safeDrawingPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                end = 16.dp + safeDrawingPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = 16.dp + safeDrawingPadding.calculateTopPadding(),
                bottom = 16.dp + safeDrawingPadding.calculateBottomPadding()
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Text(
                text = stringResource(R.string.mcp_server_control),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.server_status),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = if (isRunning) stringResource(R.string.running) else stringResource(R.string.stopped),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.toggle_server),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Switch(
                            checked = isRunning,
                            onCheckedChange = onToggleServer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRunning) {
                        Instructions(getConnectionUrl, authToken)
                        Spacer(modifier = Modifier.height(16.dp))
                        ConnectionDiagnostics(getConnectionUrl, isOnWifi)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        items(tools) { tool ->

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Checkbox(
                        checked = toolEnabledStates[tool.id] == true,
                        onCheckedChange = { onToggleTool(tool) }
                    )
                }

            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectionDiagnostics(getConnectionUrl: () -> String, isOnWifi: () -> Boolean) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testSucceeded by remember { mutableStateOf<Boolean?>(null) }

    val healthUrl = getConnectionUrl().removeSuffix("/sse") + "/health"

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isOnWifi()) {
            Text(
                text = stringResource(R.string.not_on_wifi_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
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
                color = if (succeeded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun Instructions(getConnectionUrl: () -> String, authToken: String = "YTpi") {
    // State for tracking if client configuration section is expanded
    var configExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val copyToClipboard: (String) -> Unit = { value ->
        clipboardManager.setText(AnnotatedString(value))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    val clientConfig = """
            {
                "mcpServers": {
                    "phone": {
                        "command": "npx",
                        "args": [
                            "mcp-remote",
                            "${getConnectionUrl().removePrefix("ws://")}",
                            "--header",
                            "Authorization: Bearer ${'\$'}{AUTH_TOKEN}",
                            "--allow-http"
                        ],
                        "env": {
                            "AUTH_TOKEN": "$authToken"
                        }
                    }
                }
            }
            """.trimIndent()

    Column {
        Spacer(modifier = Modifier.height(16.dp))

        // Connection URL row with copy button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.connection_url),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = getConnectionUrl(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            IconButton(onClick = { copyToClipboard(getConnectionUrl()) }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy_connection_url)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Auth token row with copy button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auth_token),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = authToken,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            IconButton(onClick = { copyToClipboard(authToken) }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy_auth_token)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // How to connect steps
        Text(
            text = stringResource(R.string.how_to_connect),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.connect_steps),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Client configuration header with expand/collapse icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { configExpanded = !configExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.mcp_client_configuration),
                style = MaterialTheme.typography.bodyLarge
            )

            Icon(
                imageVector = if (configExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (configExpanded) stringResource(R.string.collapse_client_configuration) else stringResource(
                    R.string.expand_client_configuration
                ),
                modifier = Modifier.size(24.dp)
            )
        }

        // Client configuration instructions - only show when expanded
        if (configExpanded) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = clientConfig,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { copyToClipboard(clientConfig) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = " " + stringResource(R.string.copy_configuration))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.scan_configuration),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    QrCode(
                        content = clientConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) {
        val size = 512
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.qr_code_description),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun McpServerControlPreview() {
    MCPServerTheme {
        McpServerControl(
            isRunning = true,
            onToggleServer = {},
            getConnectionUrl = { "http://192.168.1.1:3001/sse" },
            tools = listOf(
                McpToolPreview("sms", "SMS Tool", true),
                McpToolPreview("ads", "Ads Tool", true)
            ),
            toolEnabledStates = mapOf(
                "sms" to true,
                "ads" to false
            ),
            onToggleTool = {}
        )
    }
}

private class McpToolPreview(
    override val id: String, override val name: String,
    override val enabledByDefault: Boolean,

    ) : McpTool {
    override val disclaim: String?
        get() = null

    override fun configure(server: Server) {

    }

    override fun requiredPermissions(): Set<String> {
        TODO("Not yet implemented")
    }
}
