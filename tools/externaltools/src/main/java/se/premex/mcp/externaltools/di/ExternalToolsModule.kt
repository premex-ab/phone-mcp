package se.premex.mcp.externaltools.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.externaltools.configurator.ExternalToolsConfigurator
import se.premex.mcp.externaltools.configurator.ExternalToolsConfiguratorImpl
import se.premex.mcp.externaltools.repositories.ExternalToolRepository
import se.premex.mcp.externaltools.repositories.ExternalToolRepositoryImpl
import se.premex.mcp.externaltools.tool.ExternalToolsTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalToolsModule {

    @Binds
    @Singleton
    abstract fun bindExternalToolsConfigurator(
        impl: ExternalToolsConfiguratorImpl
    ): ExternalToolsConfigurator

    @Binds
    @Singleton
    abstract fun bindExternalToolRepository(
        impl: ExternalToolRepositoryImpl
    ): ExternalToolRepository

    @Binds
    @IntoSet
    abstract fun bindExternalToolsTool(
        externalToolsTool: ExternalToolsTool
    ): McpTool
}
