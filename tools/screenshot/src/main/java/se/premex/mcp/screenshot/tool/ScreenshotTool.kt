package se.premex.mcp.screenshot.tool

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.screenshot.MyMediaProjectionService
import se.premex.mcp.screenshot.configurator.ScreenshotToolConfiguratorImpl

class ScreenshotTool(
    val screenshotToolConfigurator: ScreenshotToolConfiguratorImpl,
    val mediaProjectionManager: MediaProjectionManager,
) : McpTool {
    override val id: String = "screenshot"
    override val name: String = "Screenshot"
    override val enabledByDefault: Boolean = false
    override val disclaim: String? = null

    override fun configure(server: Server) {
        screenshotToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        // Note: MediaProjection permission is handled separately through an Intent
        // This doesn't work with standard Android permission system
        return setOf()
    }
}

fun ComponentActivity.mediaRecordPermissionLauncher(): ActivityResultLauncher<Intent> {
    return registerForActivityResult(
        StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startService(
                MyMediaProjectionService.getStartIntent(
                    this,
                    result.resultCode,
                    result.data!!
                )
            );

        }
    }
}