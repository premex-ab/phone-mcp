package se.premex.mcp.files.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.files.configurator.FilesToolConfiguratorImpl
import se.premex.mcp.files.repositories.FilesRepository
import se.premex.mcp.files.repositories.FilesRepositoryImpl
import se.premex.mcp.files.tool.FilesTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FilesToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideFilesTool(filesRepository: FilesRepository): McpTool {
        return FilesTool(FilesToolConfiguratorImpl(filesRepository))
    }

    @Provides
    @Singleton
    fun provideFilesRepository(@ApplicationContext context: Context): FilesRepository {
        return FilesRepositoryImpl(context)
    }
}
