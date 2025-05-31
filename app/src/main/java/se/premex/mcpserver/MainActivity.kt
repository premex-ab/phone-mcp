package se.premex.mcpserver

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import se.premex.mcpserver.ui.theme.MCPServerTheme

class MainActivity : ComponentActivity() {
    // Service status flag
    private var isServerRunning = mutableStateOf(false)

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
            MCPServerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    McpServerControl(
                        isRunning = isServerRunning.value,
                        onToggleServer = { shouldStart ->
                            // Check permissions only when trying to start the service
                            if (shouldStart) {
                                checkNotificationPermission()
                            } else {
                                // No permission needed to stop the service
                                toggleService(false)
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    toggleService(true)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Could show educational UI here explaining why notifications are important
                    // For simplicity, we're just requesting directly
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android 12 and below, notification permission is granted by default
            toggleService(true)
        }
    }

    private fun toggleService(start: Boolean) {
        val serviceIntent = Intent(this, McpServerService::class.java)

        if (start) {
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
}

@Composable
fun McpServerControl(
    isRunning: Boolean,
    onToggleServer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MCP Server Control",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

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

                Text(
                    text = "Connection URL:",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "http://localhost:3001/sse",
                    style = MaterialTheme.typography.bodyMedium
                )
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
            onToggleServer = {}
        )
    }
}
