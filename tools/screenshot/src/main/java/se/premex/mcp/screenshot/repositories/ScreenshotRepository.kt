package se.premex.mcp.screenshot.repositories

import android.media.projection.MediaProjection

interface ScreenshotRepository {
    suspend fun captureScreenshot(mediaProjection: MediaProjection): ScreenshotInfo
}
