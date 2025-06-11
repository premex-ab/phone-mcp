package se.premex.mcp.sms.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.adserver.mcp.ads.appendSmsTools
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.sms.SmsSender
import se.premex.mcp.sms.SmsSenderImpl
import javax.inject.Singleton

class SmsTool(val smsSender: SmsSender) : McpTool {
    override val id: String = "sms"
    override val name: String = "Send SMS"
    override val enabledByDefault: Boolean = true
    override fun configure(server: Server) {
        appendSmsTools(server, smsSender)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf(android.Manifest.permission.SEND_SMS)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SmsToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideSmsTool(smsSender: SmsSender): McpTool {
        return SmsTool(smsSender)
    }

    @Provides
    @Singleton
    fun provideSmsSender(@ApplicationContext context: Context): SmsSender {
        return SmsSenderImpl(context)
    }

}
