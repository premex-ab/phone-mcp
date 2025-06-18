package se.premex.mcp.screenshot.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.screenshot.repositories.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenshotToolConfiguratorImpl(
    private val screenshotRepository: ScreenshotRepository
) : ScreenshotToolConfigurator {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun configure(server: Server) {
        /*
        // Define an action for capturing screenshots
        server.defineAction("captureScreenshot") { _, response ->
            scope.launch {
                try {
                    val screenshot = screenshotRepository.captureScreenshot()
                    response.succeed(
                        mapOf(
                            "timestamp" to screenshot.timestamp,
                            "width" to screenshot.width,
                            "height" to screenshot.height,
                            "format" to screenshot.format,
                            "base64Image" to screenshot.base64Image
                        )
                    )
                } catch (e: Exception) {
                    response.fail("Failed to capture screenshot: ${e.message}")
                }
            }
        }
         */
    }
}
