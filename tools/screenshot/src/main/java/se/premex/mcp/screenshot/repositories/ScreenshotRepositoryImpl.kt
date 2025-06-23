package se.premex.mcp.screenshot.repositories

import se.premex.mcp.screenshot.MyMediaProjectionService

class ScreenshotRepositoryImpl(
) : ScreenshotRepository {


    override fun isServiceRunning(): Boolean {
        return MyMediaProjectionService.getInstance() != null
    }
}
