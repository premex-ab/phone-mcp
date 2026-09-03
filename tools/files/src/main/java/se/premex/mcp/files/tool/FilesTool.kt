package se.premex.mcp.files.tool

import se.premex.mcp.files.R

import android.Manifest
import android.os.Build
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.files.configurator.FilesToolConfigurator

class FilesTool(
    private val filesToolConfigurator: FilesToolConfigurator,
) : McpTool {
    override val id: String = "files"
    override val name: String = "File access"
    override val nameRes: Int = R.string.tools_files_name
    override val enabledByDefault: Boolean = false
    override val disclaimRes: Int = R.string.tools_files_disclaimer

    override val disclaim: String?
        get() = "PRIVACY WARNING: Enabling file access\n\n" +
                "By enabling this tool, you grant this application and any connected AI services permission to:\n" +
                "• Browse files and directories on this device's shared storage\n" +
                "• Read the content of files, including photos, documents and downloads\n\n" +
                "You acknowledge that:\n" +
                "• Files on your device may contain sensitive personal information\n" +
                "• Connected AI services may process file contents according to their privacy policies\n" +
                "• On Android 13+ access to shared storage is limited to media files (images, video, audio)\n" +
                "• You can revoke access at any time by disabling this tool\n\n" +
                "We do not store your files, but connected AI services may process this information according to their privacy policies."

    override fun configure(server: Server) {
        filesToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            setOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
