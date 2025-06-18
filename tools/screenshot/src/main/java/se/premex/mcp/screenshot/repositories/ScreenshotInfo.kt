package se.premex.mcp.screenshot.repositories

data class ScreenshotInfo(
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val format: String,
    val base64Image: String
)
