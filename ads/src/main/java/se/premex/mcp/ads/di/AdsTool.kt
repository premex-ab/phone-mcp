package se.premex.mcp.ads.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton

class AdsTool : McpTool {
    override val id: String = "ads"
    override val name: String = "ADS Tool"
    override val enabledByDefault: Boolean = false
}

@Module
@InstallIn(SingletonComponent::class)
object AdsToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideAdsTool(): McpTool {
        return AdsTool()
    }
}
