package se.premex.mcp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.auth.AuthRepository
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.data.ServerPreferencesRepository
import se.premex.mcp.di.ToolService
import se.premex.mcp.review.ReviewPrompter
import se.premex.mcp.ui.ConnectionGuide
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
            val onboardingCompleted by serverPreferencesRepository.isOnboardingCompleted()
                .collectAsState(initial = true)
            val hasClientConnected by serverPreferencesRepository.hasClientConnected()
                .collectAsState(initial = false)
            val reviewPrompted by serverPreferencesRepository.isReviewPrompted()
                .collectAsState(initial = true)

            // Ask for a Play Store review once, after the user has had their
            // first successful MCP client connection — the moment the app has
            // proven its value
            LaunchedEffect(hasClientConnected, reviewPrompted) {
                if (hasClientConnected && !reviewPrompted) {
                    serverPreferencesRepository.markReviewPrompted()
                    ReviewPrompter.requestReview(this@MainActivity)
                }
            }

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

                        // First-run onboarding explaining what the app is and how to connect
                        if (!onboardingCompleted) {
                            OnboardingDialog(
                                onDismiss = { serverPreferencesRepository.setOnboardingCompleted() }
                            )
                        }

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

    @Composable
    private fun OnboardingDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.onboarding_title)) },
            text = { Text(stringResource(R.string.onboarding_body)) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.get_started))
                }
            }
        )
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp + safeDrawingPadding.calculateLeftPadding(LayoutDirection.Ltr),
            end = 16.dp + safeDrawingPadding.calculateRightPadding(LayoutDirection.Ltr),
            top = 8.dp,
            bottom = 24.dp + safeDrawingPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ServerStatusCard(
                isRunning = isRunning,
                onToggleServer = onToggleServer,
                getConnectionUrl = getConnectionUrl
            )
        }

        if (isRunning) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ConnectionGuide(
                            connectionUrl = getConnectionUrl(),
                            authToken = authToken,
                            isOnWifi = isOnWifi
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.tools_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.tools_connect_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    tools.forEachIndexed { index, tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = toolEnabledStates[tool.id] == true,
                                onCheckedChange = { onToggleTool(tool) }
                            )
                        }
                        if (index < tools.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero card: status dot + label + switch in one row, container tinted green
 * while the server is running so state is readable at a glance.
 */
@Composable
private fun ServerStatusCard(
    isRunning: Boolean,
    onToggleServer: (Boolean) -> Unit,
    getConnectionUrl: () -> String,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "statusContainer"
    )
    val contentColor = if (isRunning) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.outline
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isRunning) R.string.server_running else R.string.server_stopped
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isRunning) {
                        getConnectionUrl().removePrefix("http://").removeSuffix("/sse")
                    } else {
                        stringResource(R.string.server_stopped_hint)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = isRunning,
                onCheckedChange = onToggleServer
            )
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
