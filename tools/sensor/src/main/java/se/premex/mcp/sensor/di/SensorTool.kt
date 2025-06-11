package se.premex.mcp.sensor.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.sensor.repositories.SensorRepository
import se.premex.mcp.sensor.repositories.SensorRepositoryImpl
import se.premex.mcp.sensor.tool.SensorTool
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object SensorToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideSensorTool(contactsRepository: SensorRepository): McpTool {
        return SensorTool(contactsRepository)
    }

    @Provides
    @Singleton
    fun provideSensorSender(@ApplicationContext context: Context): SensorRepository {
        return SensorRepositoryImpl(context)
    }

}
