package se.premex.mcp.screenshot.configurator

import android.graphics.Bitmap
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import se.premex.mcp.screenshot.repositories.BitmapStorage
import java.io.ByteArrayOutputStream
import java.util.Base64

class ScreenshotToolConfiguratorImpl(
    private val bitmapStorage: BitmapStorage
) : ScreenshotToolConfigurator {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun configure(server: Server) {
        server.addTool(
            name = "phone_take_screenshot",
            description = """
                gets the phone screenshot and returns it as an image.
            """.trimIndent(),

            ) { request ->

            try {

                val bitmap: Bitmap = bitmapStorage.retrieve()!!

                val bytes: ByteArray = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    stream.toByteArray()
                }

                return@addTool CallToolResult(
                    content = listOf(
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
                        TextContent("Error taking photo: ${e.message}")
                    )
                )
            }
        }
    }
}
