package se.premex.mcp.camera.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.camera.repositories.CameraRepository
import se.premex.mcp.camera.repositories.CameraRepositoryImpl
import se.premex.mcp.camera.configurator.CameraToolConfiguratorImpl
import se.premex.mcp.camera.tool.CameraTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideCameraTool(@ApplicationContext context: Context): McpTool {
        val cameraRepository: CameraRepository = CameraRepositoryImpl(context)
        val cameraToolConfigurator = CameraToolConfiguratorImpl(cameraRepository)
        return CameraTool(cameraToolConfigurator)
    }
}
