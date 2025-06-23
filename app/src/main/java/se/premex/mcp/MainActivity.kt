package se.premex.mcp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.auth.AuthRepository
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.di.ToolService
import se.premex.mcp.input.repositories.InputRepository
import se.premex.mcp.screenshot.repositories.ScreenshotRepository
import se.premex.mcp.screenshot.tool.mediaRecordPermissionLauncher
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
    lateinit var mediaProjectionManager: MediaProjectionManager

    @Inject
    lateinit var inputRepository: InputRepository

    @Inject
    lateinit var screenshotRepository: ScreenshotRepository

    val startMediaProjection: ActivityResultLauncher<Intent> = mediaRecordPermissionLauncher()

    fun mediaPermissionsPlease(): Boolean {
        if (!screenshotRepository.isServiceRunning()) {
            startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
            return false
        }
        return true
    }

    fun startInputService(): Boolean {
        if (!inputRepository.isAccessibilityServiceRunning()) {
            inputRepository.startAccessibilityServiceIfAlreadyRunning()
            return false
        }
        return true
    }

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
                "All permissions are required to run the MCP server service",
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
                "permission is required to run the MCP server service",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
                val intent = Intent("se.premex.mcp.MCP_PROVIDER")

                val resolveInfoList = packageManager.queryIntentContentProviders(
                    intent,
                    PackageManager.MATCH_ALL
                )

                resolveInfoList.toString()
        */
        enableEdgeToEdge()
        setContent {
            val toolStates by toolService.toolEnabledStates.collectAsState()

            // Extract the auth token from the instructions string
            // The format is "Please use the token 'XXXXXX' to authenticate your connection."
            val authInstructions = authRepository.getConnectionInstructions()
            val authToken = authInstructions.substringAfter("'").substringBefore("'")

            MCPServerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    McpServerControl(
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
                        modifier = Modifier.padding(innerPadding),
                        getConnectionUrl = { getConnectionUrl() },
                        tools = toolService.tools.toList(),
                        toolEnabledStates = toolStates,
                        onToggleTool = { tool ->
                            handleToolToggle(tool)
                        },
                        authToken = authToken,
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
            }
        }
    }

    private fun startRequiredToolServices(): Boolean {
        var allStarted = true
        // If screenshot tool is enabled, start media projection
        if (toolService.toolEnabledStates.value["screenshot"] == true) {
            allStarted = allStarted && mediaPermissionsPlease()
        }
        // If input tool is enabled, start accessibility service
        if (toolService.toolEnabledStates.value["input"] == true) {
            allStarted = allStarted && startInputService()
        }
        return allStarted
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
            // Only start the service if all required tool services are running
            if (!startRequiredToolServices()) {
                // Optionally, show a message to the user here
                return
            }
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

    private fun getConnectionUrl(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        return "http://$ipAddress:3001/sse"
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
            title = { Text("Warning for ${tool.name}") },
            text = { Text(tool.disclaim ?: "No description available.") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
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
    authToken: String = "YTpi"
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Text(
                text = "MCP Server Control",
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
                        text = "Server Status:",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = if (isRunning) "RUNNING" else "STOPPED",
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
                            text = "Toggle Server",
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
                        onCheckedChange = { isChecked ->
                            onToggleTool(tool)
                        },
                        enabled = !isRunning // Disable toggle when service is running
                    )
                }

            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Instructions(getConnectionUrl: () -> String, authToken: String = "YTpi") {
    // State for tracking if client configuration section is expanded
    var configExpanded by remember { mutableStateOf(false) }

    Column {
        Spacer(modifier = Modifier.height(16.dp))

        // Connection URL row with expand/collapse icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connection URL:",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = getConnectionUrl(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Clickable icon to expand/collapse client config
            Icon(
                imageVector = if (configExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (configExpanded) "Collapse client configuration" else "Expand client configuration",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { configExpanded = !configExpanded }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Client configuration instructions - only show when expanded
        if (configExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "MCP Client Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = """
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
                                    """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
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
            onToggleTool = {},
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
