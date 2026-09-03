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
import se.premex.mcp.camera.appfunctions.CameraAppFunctions
import se.premex.mcp.appfunctions.service.AppFunctionFactoryRegistration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideCameraTool(cameraToolConfigurator: CameraToolConfiguratorImpl): McpTool =
        CameraTool(cameraToolConfigurator)

    @Provides
    @Singleton
    fun provideCameraRepository(@ApplicationContext context: Context): CameraRepository =
        CameraRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideCameraToolConfigurator(
        cameraRepository: CameraRepository,
    ): CameraToolConfiguratorImpl = CameraToolConfiguratorImpl(cameraRepository)

    @Provides
    @Singleton
    @IntoSet
    fun provideCameraAppFunctionFactory(
        functions: CameraAppFunctions,
    ): AppFunctionFactoryRegistration = AppFunctionFactoryRegistration { builder ->
        builder.addEnclosingClassFactory(CameraAppFunctions::class.java) { functions }
    }
}
