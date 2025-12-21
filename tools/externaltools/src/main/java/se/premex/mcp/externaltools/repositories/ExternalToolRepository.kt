package se.premex.mcp.externaltools.repositories

/**
 * Repository interface for managing external tools registration information
 */
interface ExternalToolRepository {
    /**
     * Save information about an external tool
     * @param toolInfo Information about the external tool to save
     */
    fun saveExternalTool(toolInfo: ExternalToolInfo)

    /**
     * Remove an external tool by its content provider authority
     * @param authority The content provider authority to remove
     */
    fun removeExternalTool(authority: String)

    /**
     * Get information about all registered external tools
     * @return List of ExternalToolInfo objects for all registered tools
     */
    fun getRegisteredTools(): List<ExternalToolInfo>

    /**
     * Check if a specific content provider authority is registered
     * @param authority The content provider authority to check
     * @return True if registered, false otherwise
     */
    fun isToolRegistered(authority: String): Boolean
}
