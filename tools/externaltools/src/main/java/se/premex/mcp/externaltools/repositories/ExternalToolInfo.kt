package se.premex.mcp.externaltools.repositories

/**
 * Data class representing information about an external tool provided by a content provider
 */
data class ExternalToolInfo(
    /** The content provider authority for this tool */
    val authority: String,

    /** The name of the tool to be registered with MCP */
    val toolName: String,

    /** Tool description */
    val description: String,

    /** JSON string representing the input schema */
    val inputSchemaJson: String,

    /** List of required field names */
    val requiredFields: List<String>,

    /** MIME type of the content provider */
    val mimeType: String = "application/vnd.mcp.tool"
)
