package se.premex.mcp.appfunctions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.appfunctions.configurator.AppFunctionsConfigurator
import se.premex.mcp.appfunctions.configurator.AppFunctionsConfiguratorImpl
import se.premex.mcp.appfunctions.tool.AppFunctionsTool
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppFunctionsModule {

    @Binds
    @Singleton
    abstract fun bindAppFunctionsConfigurator(
        impl: AppFunctionsConfiguratorImpl,
    ): AppFunctionsConfigurator

    @Binds
    @IntoSet
    abstract fun bindAppFunctionsTool(
        tool: AppFunctionsTool,
    ): McpTool
}
