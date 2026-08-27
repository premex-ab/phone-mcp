package se.premex.mcp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.premex.mcp.R
import se.premex.mcp.ui.theme.MCPServerTheme

/**
 * Step-by-step guide for connecting an MCP client to this server.
 * Shown on the home screen while the server is running.
 */
@Composable
fun ConnectionGuide(
    connectionUrl: String,
    authToken: String,
    modifier: Modifier = Modifier,
) {
    var selectedClient by rememberSaveable { mutableStateOf(McpClient.CLAUDE_CODE) }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.connect_your_ai_client),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            McpClient.entries.forEach { client ->
                FilterChip(
                    selected = selectedClient == client,
                    onClick = { selectedClient = client },
                    label = { Text(stringResource(client.title)) }
                )
            }
        }

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
        Text(
            text = stringResource(R.string.tools_connect_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The MCP clients the guide can generate ready-to-paste setup snippets for. */
private enum class McpClient(
    @StringRes val title: Int,
    @StringRes val instructions: Int,
    @StringRes val copyButtonText: Int,
) {
    CLAUDE_CODE(
        title = R.string.client_claude_code,
        instructions = R.string.claude_code_instructions,
        copyButtonText = R.string.copy_command,
    ) {
        override fun snippet(url: String, token: String): String =
            "claude mcp add --transport sse phone $url " +
                "--header \"Authorization: Bearer $token\""
    },
    CLAUDE_DESKTOP(
        title = R.string.client_claude_desktop,
        instructions = R.string.claude_desktop_instructions,
        copyButtonText = R.string.copy_configuration,
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
    },
    OTHER(
        title = R.string.client_other,
        instructions = R.string.other_client_instructions,
        copyButtonText = R.string.copy_details,
    ) {
        override fun snippet(url: String, token: String): String =
            "URL: $url\nHeader: Authorization: Bearer $token\nTransport: SSE over HTTP"
    };

    abstract fun snippet(url: String, token: String): String
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
            containerColor = MaterialTheme.colorScheme.surface
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
