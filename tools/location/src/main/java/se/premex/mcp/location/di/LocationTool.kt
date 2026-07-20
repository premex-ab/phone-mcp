package se.premex.mcp.location.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
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
    fun provideLocationTool(@ApplicationContext context: Context): McpTool {
        val locationRepository: LocationRepository = LocationRepositoryImpl(context)
        val locationToolConfigurator = LocationToolConfiguratorImpl(locationRepository)
        return LocationTool(locationToolConfigurator)
    }
}
