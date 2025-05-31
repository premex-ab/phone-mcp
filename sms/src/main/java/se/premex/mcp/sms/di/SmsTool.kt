package se.premex.mcp.sms.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.adserver.mcp.ads.appendSmsTools
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton

class SmsTool : McpTool {
    override val id: String = "sms"
    override val name: String = "SMS Tool"
    override val enabledByDefault: Boolean = true
    override fun configure(server: Server) {
        appendSmsTools(server)
    }
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
