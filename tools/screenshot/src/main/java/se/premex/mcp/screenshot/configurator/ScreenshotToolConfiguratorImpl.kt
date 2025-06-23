package se.premex.mcp.screenshot.configurator

import android.graphics.Bitmap
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import se.premex.mcp.screenshot.repositories.BitmapStorage
import se.premex.mcp.screenshot.repositories.DisplayInfoRepository
import java.io.ByteArrayOutputStream
import java.util.Base64

class ScreenshotToolConfiguratorImpl(
    private val bitmapStorage: BitmapStorage,
    private val displayInfoRepository: DisplayInfoRepository
) : ScreenshotToolConfigurator {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun configure(server: Server) {
        server.addTool(
            name = "phone_take_screenshot",
            description = """
                Gets the phone screenshot and returns it as an image.
            """.trimIndent(),

            ) { request ->

            try {
                // Get scale parameter from the request, default to 0.5 if not provided
                val scale = 0.5f

                val originalBitmap: Bitmap = bitmapStorage.retrieve()!!

                // Get actual screen dimensions
                val displayInfo = displayInfoRepository.getDisplayInfo()
                val screenWidth = displayInfo.widthPixels
                val screenHeight = displayInfo.heightPixels

                // Resize bitmap if scale is less than 1.0
                val bitmap = if (scale < 1.0f) {
                    val newWidth = (originalBitmap.width * scale).toInt()
                    val newHeight = (originalBitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                } else {
                    originalBitmap
                }

                // Adjust JPEG quality based on scale (lower scale = lower quality is acceptable)
                val jpegQuality = (90 * scale).toInt().coerceIn(50, 100)

                val bytes: ByteArray = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                    stream.toByteArray()
                }

                // Clean up the scaled bitmap if it's different from the original
                if (bitmap != originalBitmap) {
                    bitmap.recycle()
                }

                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "Screenshot taken successfully. Actual screen size: ${screenWidth}x${screenHeight}" +
                                    if (scale < 1.0f) " (image scaled to ${bitmap.width}x${bitmap.height}) when analyzing where things are on screen, take scaling into consideration" else ""
                        ),
                        ImageContent(
                            data = Base64.getEncoder().encodeToString(bytes),
                            mimeType = "image/jpeg"
                        ),
                        TextContent("Photo captured successfully.")
                    )
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(
                        TextContent("Error taking screenshot: ${e.message}")
                    )
                )
            }
        }
    }
}
