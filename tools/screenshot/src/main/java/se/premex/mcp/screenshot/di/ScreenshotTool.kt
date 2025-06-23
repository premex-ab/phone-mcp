package se.premex.mcp.screenshot.di

import android.content.Context
import android.media.projection.MediaProjectionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.screenshot.configurator.ScreenshotToolConfiguratorImpl
import se.premex.mcp.screenshot.repositories.BitmapStorage
import se.premex.mcp.screenshot.repositories.InMemoryBitmapStorage
import se.premex.mcp.screenshot.tool.ScreenshotTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScreenshotToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideScreenshotTool(
        bitmapStorage: BitmapStorage,
        mediaProjectionManager: MediaProjectionManager,
    ): McpTool {
        val screenshotToolConfigurator = ScreenshotToolConfiguratorImpl(bitmapStorage)
        return ScreenshotTool(screenshotToolConfigurator, mediaProjectionManager)
    }

    @Provides
    @Singleton
    fun provideBitmapStorage(@ApplicationContext context: Context): BitmapStorage {
        return InMemoryBitmapStorage()
        //return DiskBitmapStorage(context)
    }

    @Provides
    @Singleton
    fun provideMediaProjectionManager(@ApplicationContext context: Context): MediaProjectionManager {
        return  context.getSystemService(MediaProjectionManager::class.java)
    }
}

