package se.premex.mcp.sms.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcpserver.di.McpTool
import javax.inject.Singleton

class SmsTool : McpTool {
    override val id: String = "sms"
    override val name: String = "SMS Tool"
    override val enabledByDefault: Boolean = true
}

@Module
@InstallIn(SingletonComponent::class)
object SmsToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideSmsTool(): McpTool {
        return SmsTool()
    }
}
