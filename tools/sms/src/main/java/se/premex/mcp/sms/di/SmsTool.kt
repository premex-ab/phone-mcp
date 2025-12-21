package se.premex.mcp.sms.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.sms.appendSmsTools
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.sms.SmsSender
import se.premex.mcp.sms.SmsSenderImpl
import javax.inject.Singleton

class SmsTool(val smsSender: SmsSender) : McpTool {
    override val id: String = "sms"
    override val name: String = "Send SMS"
    override val enabledByDefault: Boolean = false
    override val disclaim: String?
        get() = "PRIVACY & COST WARNING: Enabling SMS access\n\n" +
                "By enabling this tool, you grant this application and any connected AI services permission to:\n" +
                "• Send SMS messages from your device to any phone number\n" +
                "• Access your SMS history for contextual responses\n" +
                "• Potentially incur charges from your mobile carrier\n\n" +
                "You acknowledge that:\n" +
                "• You are responsible for any messages sent and associated costs\n" +
                "• You are responsible for ensuring any AI services you connect to will not misuse this capability\n" +
                "• You can revoke access at any time by disabling this tool\n\n" +
                "We do not store the content of your messages, but connected AI services may process this information according to their privacy policies."

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
