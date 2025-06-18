package se.premex.mcp.screenshot.di

import android.content.Context
import android.media.projection.MediaProjection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.screenshot.repositories.ScreenshotRepository
import se.premex.mcp.screenshot.repositories.ScreenshotRepositoryImpl
import se.premex.mcp.screenshot.configurator.ScreenshotToolConfiguratorImpl
import se.premex.mcp.screenshot.tool.ScreenshotTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScreenshotToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideScreenshotTool(
        @ApplicationContext context: Context,
        mediaProjection: MediaProjection?
    ): McpTool {
        val screenshotRepository: ScreenshotRepository = ScreenshotRepositoryImpl(context, mediaProjection)
        val screenshotToolConfigurator = ScreenshotToolConfiguratorImpl(screenshotRepository)
        return ScreenshotTool(screenshotToolConfigurator)
    }

    // Note: MediaProjection is typically obtained from user permission dialog
    // This is a placeholder - in a real implementation we would need to handle
    // requesting and storing the MediaProjection permission
    @Provides
    @Singleton
    fun provideMediaProjection(): MediaProjection? {
        // In a real implementation, this would come from the result of
        // startActivityForResult with MediaProjection permission request
        return null
    }
}

class media() {

}