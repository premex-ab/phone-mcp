package se.premex.mcp.input.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.input.configurator.InputToolConfigurator
import se.premex.mcp.input.configurator.InputToolConfiguratorImpl
import se.premex.mcp.input.repositories.InputRepository
import se.premex.mcp.input.repositories.InputRepositoryImpl
import se.premex.mcp.input.tool.InputTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InputToolModule {

    @Binds
    abstract fun bindInputToolConfigurator(impl: InputToolConfiguratorImpl): InputToolConfigurator

    @Binds
    abstract fun bindInputRepository(impl: InputRepositoryImpl): InputRepository

    companion object {
        @Provides
        @Singleton
        @IntoSet
        fun provideInputTool(configurator: InputToolConfigurator): McpTool {
            return InputTool(configurator)
        }
    }
}
