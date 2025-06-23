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
import se.premex.mcp.screenshot.repositories.DisplayInfoRepository
import se.premex.mcp.screenshot.repositories.DisplayInfoRepositoryImpl
import se.premex.mcp.screenshot.repositories.InMemoryBitmapStorage
import se.premex.mcp.screenshot.repositories.ScreenshotRepository
import se.premex.mcp.screenshot.repositories.ScreenshotRepositoryImpl
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
        displayInfoRepository: DisplayInfoRepository,
        mediaProjectionManager: MediaProjectionManager,
    ): McpTool {
        val screenshotToolConfigurator = ScreenshotToolConfiguratorImpl(bitmapStorage, displayInfoRepository)
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
    fun provideDisplayInfoRepository(@ApplicationContext context: Context): DisplayInfoRepository {
        return DisplayInfoRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideScreenshotRepository(): ScreenshotRepository {
        return ScreenshotRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideMediaProjectionManager(@ApplicationContext context: Context): MediaProjectionManager {
        return context.getSystemService(MediaProjectionManager::class.java)
    }
}
