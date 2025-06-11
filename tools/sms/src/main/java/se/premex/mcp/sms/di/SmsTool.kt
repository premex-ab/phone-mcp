package se.premex.mcp.sms.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.adserver.mcp.ads.appendSmsTools
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.sms.SmsSender
import se.premex.mcp.sms.SmsSenderImpl
import javax.inject.Singleton

class SmsTool(val smsSender: SmsSender) : McpTool {
    override val id: String = "sms"
    override val name: String = "SMS Tool"
    override val enabledByDefault: Boolean = true
    override fun configure(server: Server) {
        appendSmsTools(server, smsSender)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf(android.Manifest.permission.SEND_SMS)
    }
}

class DummyTool(dummyId: String, dummyName: String) : McpTool {
    override val id: String = dummyId
    override val name: String = dummyName
    override val enabledByDefault: Boolean = false
    override fun configure(server: Server) {

    }

    override fun requiredPermissions(): Set<String> {
        TODO("Not yet implemented")
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

    @Provides
    @Singleton
    @ElementsIntoSet
    fun provideDummyTools(): Set<McpTool> {
        return HashSet<McpTool>().apply {
            add(DummyTool("dummy1", "Dummy Tool 1"))
            add(DummyTool("dummy2", "Dummy Tool 2"))
            add(DummyTool("dummy3", "Dummy Tool 3"))
            add(DummyTool("dummy4", "Dummy Tool 4"))
            add(DummyTool("dummy5", "Dummy Tool 5"))
            add(DummyTool("dummy6", "Dummy Tool 6"))
            add(DummyTool("dummy7", "Dummy Tool 7"))
        }
    }
}
