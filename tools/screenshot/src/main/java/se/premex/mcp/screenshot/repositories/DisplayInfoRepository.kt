package se.premex.mcp.screenshot.repositories

/**
 * Repository to retrieve display information from the device
 */
interface DisplayInfoRepository {
    /**
     * Get the display information including screen dimensions
     */
    fun getDisplayInfo(): DisplayInfo
}
