package se.premex.mcp.ads.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.adserver.mcp.ads.appendAdTools
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton

class AdsTool : McpTool {
    override val id: String = "ads"
    override val name: String = "ADS Tool"
    override val enabledByDefault: Boolean = false
    override val disclaim: String?
        get() = null

    override fun configure(server: Server) {
        appendAdTools(server, "da9f87c34f4641a4a2bdace0ff4895fe")
    }

    override fun requiredPermissions(): Set<String> {
        return emptySet()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AdsToolModule {

//    @Provides
//    @Singleton
//    @IntoSet
//    fun provideAdsTool(): McpTool {
//        return AdsTool()
//    }
}
