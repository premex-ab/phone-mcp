package se.premex.mcpserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import se.premex.mcp.core.tool.McpTool
import se.premex.mcpserver.di.ToolService
import se.premex.mcpserver.ui.theme.MCPServerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Service status flag
    private var isServerRunning = mutableStateOf(false)

    @Inject
    lateinit var toolService: ToolService

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
                "Notification permission is required to run the MCP server service",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val toolStates by toolService.toolEnabledStates.collectAsState()

            MCPServerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    McpServerControl(
                        isRunning = isServerRunning.value,
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
                        onToggleToolEnabled = { toolId ->
                            toolService.toggleToolEnabled(toolId)
                        }
                    )
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
            // Pass all tool states as an extra
            serviceIntent.putExtra(
                McpServerService.EXTRA_TOOL_STATES,
                HashMap(toolService.toolEnabledStates.value)
            )

            // Start service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isServerRunning.value = true
        } else {
            stopService(serviceIntent)
            isServerRunning.value = false
        }
    }

    private fun getConnectionUrl(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        return "http://$ipAddress:3001/sse"
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
    onToggleToolEnabled: (String) -> Unit
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
                        Instructions(getConnectionUrl)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        items(tools) { tool ->
            var expanded by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Enable ${tool.name}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Checkbox(
                        checked = toolEnabledStates[tool.id] == true,
                        onCheckedChange = { onToggleToolEnabled(tool.id) }
                    )
                }

                if (expanded) {
                    // Show additional information or controls for the tool
                    Text(
                        text = "Additional settings for ${tool.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    // You can add more UI elements here for tool-specific settings

                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Collapse",
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.End)
                            .padding(end = 16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.End)
                            .padding(end = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Instructions(getConnectionUrl: () -> String) {
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

                    Text(
                        text = "To connect Claude Desktop or other MCP clients, add this to your claude_desktop_config.json:",
                        style = MaterialTheme.typography.bodySmall
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
                                                    "http://${getConnectionUrl().removePrefix("ws://")}",
                                                    "--header",
                                                    "Authorization: Bearer ${'\$'}{AUTH_TOKEN}",
                                                    "--allow-http"
                                                ],
                                                "env": {
                                                    "AUTH_TOKEN": "YTpi"
                                                }
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Location: ~/Library/Application Support/Claude/claude_desktop_config.json",
                        style = MaterialTheme.typography.bodySmall
                    )
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
            onToggleToolEnabled = {}
        )
    }
}

private class McpToolPreview(
    override val id: String, override val name: String,
    override val enabledByDefault: Boolean,

    ) : McpTool {
    override fun configure(server: Server) {

    }

    override fun requiredPermissions(): Set<String> {
        TODO("Not yet implemented")
    }
}
