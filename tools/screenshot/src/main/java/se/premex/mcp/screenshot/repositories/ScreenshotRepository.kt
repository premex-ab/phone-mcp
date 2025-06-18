package se.premex.mcp.screenshot.repositories

interface ScreenshotRepository {
    suspend fun captureScreenshot(): ScreenshotInfo
}
