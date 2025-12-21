package se.premex.mcp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import se.premex.mcp.auth.AuthRepository
import javax.inject.Singleton

/**
 * Hilt module to provide repository instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    /**
     * Provides the AuthRepository instance with application context for DataStore.
     */
    @Provides
    @Singleton
    fun provideAuthRepository(@ApplicationContext context: Context): AuthRepository {
        return AuthRepository(context)
    }
}
