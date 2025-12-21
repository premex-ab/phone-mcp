package se.premex.mcp.sms.di

import android.Manifest
import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.sms.SmsIntentSender
import se.premex.mcp.sms.SmsIntentSenderImpl
import se.premex.mcp.sms.appendSmsTools
import javax.inject.Singleton

class SmsIntentTool(val smsIntentSender: SmsIntentSender) : McpTool {
    override val id: String = "sms"
    override val name: String = "Send SMS Intent"
    override val enabledByDefault: Boolean = false
    override val disclaim: String?
        get() = "PRIVACY NOTICE: SMS Intent Creation\n\n" +
                "By enabling this tool, you grant this application and any connected AI services permission to:\n" +
                "• Prepare SMS messages for you to review and manually send\n" +
                "• Create SMS sending intents that you must approve before sending\n\n" +
                "You acknowledge that:\n" +
                "• This tool CANNOT automatically send messages without your approval\n" +
                "• You will always have the final review and decision before any message is sent\n" +
                "• You are responsible for any messages you choose to send and their associated costs\n" +
                "• You can revoke access at any time by disabling this tool\n\n" +
                "We do not store the content of your messages, but connected AI services may process this information according to their privacy policies."

    override fun configure(server: Server) {
        appendSmsTools(server, smsIntentSender)
    }

    override fun requiredPermissions(): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setOf()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SmsToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideSmsTool(smsSender: SmsIntentSender): McpTool {
        return SmsIntentTool(smsSender)
    }

    @Provides
    @Singleton
    fun provideSmsSender(@ApplicationContext context: Context): SmsIntentSender {
        return SmsIntentSenderImpl(context)
    }

}
