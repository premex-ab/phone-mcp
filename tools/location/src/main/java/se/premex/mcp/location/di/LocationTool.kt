package se.premex.mcp.location.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.appfunctions.service.AppFunctionFactoryRegistration
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.location.appfunctions.LocationAppFunctions
import se.premex.mcp.location.configurator.LocationToolConfiguratorImpl
import se.premex.mcp.location.repositories.LocationRepository
import se.premex.mcp.location.repositories.LocationRepositoryImpl
import se.premex.mcp.location.tool.LocationTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideLocationTool(locationRepository: LocationRepository): McpTool {
        val locationToolConfigurator = LocationToolConfiguratorImpl(locationRepository)
        return LocationTool(locationToolConfigurator)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
    ): LocationRepository = LocationRepositoryImpl(context)

    @Provides
    @Singleton
    @IntoSet
    fun provideLocationAppFunctionFactory(
        functions: LocationAppFunctions,
    ): AppFunctionFactoryRegistration = AppFunctionFactoryRegistration { builder ->
        builder.addEnclosingClassFactory(LocationAppFunctions::class.java) { functions }
    }
}
