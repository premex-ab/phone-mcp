package se.premex.mcp.files.configurator

import android.util.Base64
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import se.premex.mcp.files.repositories.FileEntry
import se.premex.mcp.files.repositories.FilesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FilesToolConfiguratorImpl(
    private val filesRepository: FilesRepository,
) : FilesToolConfigurator {

    override fun configure(server: Server) {
        addListFilesTool(server)
        addFileInfoTool(server)
        addReadFileTool(server)
    }

    private fun addListFilesTool(server: Server) {
        server.addTool(
            name = "phone_list_files",
            description = """
                List files and directories on the android device.
                Call without a path to see the available storage roots:
                - shared: the device's shared storage (photos, downloads, documents, ...)
                - app-files, app-cache, app-external: this app's own directories
                Then call again with a path like "shared/Download" to browse into it.
                Note: on Android 13+ only media files (images, video, audio) and files
                created by this app are readable in shared storage.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", "string")
                        put(
                            "description",
                            "Directory to list, e.g. 'shared/DCIM'. Omit to list the storage roots."
                        )
                    }
                }
            )
        ) { request ->
            val path = request.arguments?.get("path")?.jsonPrimitive?.content

            val entries = try {
                if (path.isNullOrBlank()) {
                    filesRepository.listRoots()
                } else {
                    filesRepository.listFiles(path)
                }
            } catch (e: Exception) {
                return@addTool errorResult("Error listing files", e)
            }

            if (entries.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("The directory '$path' is empty."))
                )
            }

            CallToolResult(
                content = entries.map { entry -> TextContent(describe(entry)) }
            )
        }
    }

    private fun addFileInfoTool(server: Server) {
        server.addTool(
            name = "phone_file_info",
            description = """
                Get metadata (size, modification time, mime type) for a file or
                directory on the android device. Paths use the same format as
                phone_list_files, e.g. 'shared/Download/report.pdf'.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "File or directory path, e.g. 'shared/DCIM/photo.jpg'")
                    }
                },
                required = listOf("path")
            )
        ) { request ->
            val path = request.arguments?.get("path")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'path' parameter is required."))
                )

            val entry = try {
                filesRepository.getFileInfo(path)
            } catch (e: Exception) {
                return@addTool errorResult("Error reading file info", e)
            }

            CallToolResult(content = listOf(TextContent(describe(entry))))
        }
    }

    private fun addReadFileTool(server: Server) {
        server.addTool(
            name = "phone_read_file",
            description = """
                Read the content of a file on the android device.
                Text files are returned as text, images as viewable image content,
                and other binary files as base64. Paths use the same format as
                phone_list_files, e.g. 'shared/Download/notes.txt'.
                Reads are capped at max_bytes (default $DEFAULT_MAX_BYTES, max $MAX_MAX_BYTES).
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "File path, e.g. 'shared/Download/notes.txt'")
                    }
                    putJsonObject("max_bytes") {
                        put("type", "integer")
                        put(
                            "description",
                            "Optional maximum number of bytes to read (default $DEFAULT_MAX_BYTES, max $MAX_MAX_BYTES)"
                        )
                    }
                },
                required = listOf("path")
            )
        ) { request ->
            val path = request.arguments?.get("path")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'path' parameter is required."))
                )
            val maxBytes = (
                request.arguments?.get("max_bytes")?.jsonPrimitive?.content?.toIntOrNull()
                    ?: DEFAULT_MAX_BYTES
                ).coerceIn(1, MAX_MAX_BYTES)

            val data = try {
                filesRepository.readFile(path, maxBytes)
            } catch (e: Exception) {
                return@addTool errorResult("Error reading file", e)
            }

            val isImage = data.mimeType?.startsWith("image/") == true
            if (isImage && data.truncated) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "The image '$path' is ${data.totalSize} bytes, which exceeds " +
                                "max_bytes ($maxBytes). Retry with a larger max_bytes " +
                                "(up to $MAX_MAX_BYTES) to read it."
                        )
                    )
                )
            }

            val summary = buildString {
                append("Path: $path, size: ${data.totalSize} bytes")
                data.mimeType?.let { append(", mime type: $it") }
                if (data.truncated) {
                    append(" (truncated to first ${data.bytes.size} bytes; retry with a larger max_bytes for more)")
                }
            }

            val content = when {
                isImage -> ImageContent(
                    data = Base64.encodeToString(data.bytes, Base64.DEFAULT),
                    mimeType = data.mimeType ?: "image/*",
                )

                isProbablyText(data.bytes) -> TextContent(String(data.bytes, Charsets.UTF_8))

                else -> TextContent(
                    "Base64-encoded binary content:\n" +
                        Base64.encodeToString(data.bytes, Base64.NO_WRAP)
                )
            }

            CallToolResult(content = listOf(TextContent(summary), content))
        }
    }

    private fun describe(entry: FileEntry): String = buildString {
        append(if (entry.isDirectory) "[DIR] " else "[FILE] ")
        append(entry.path)
        if (!entry.isDirectory) {
            append(" (${entry.sizeBytes} bytes")
            entry.mimeType?.let { append(", $it") }
            append(")")
        }
        if (entry.lastModified > 0) {
            append(" modified ${formatTimestamp(entry.lastModified)}")
        }
    }

    private fun formatTimestamp(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMillis))

    /** Treats content without NUL bytes as text. */
    private fun isProbablyText(bytes: ByteArray): Boolean = !bytes.contains(0)

    private fun errorResult(prefix: String, e: Exception): CallToolResult = CallToolResult(
        content = listOf(TextContent("$prefix: ${e.message}"))
    )

    companion object {
        private const val DEFAULT_MAX_BYTES = 1_000_000
        private const val MAX_MAX_BYTES = 5_000_000
    }
}
