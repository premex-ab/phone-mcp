package se.premex.mcp.contacts.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.contacts.repositories.ContactsRepository
import se.premex.mcp.contacts.repositories.ContactsRepositoryImpl
import se.premex.mcp.contacts.tool.ContactsTool
import se.premex.mcp.contacts.appfunctions.ContactsAppFunctions
import se.premex.mcp.appfunctions.service.AppFunctionFactoryRegistration
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object ContactsToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideContactsTool(contactsRepository: ContactsRepository): McpTool {
        return ContactsTool(contactsRepository)
    }

    @Provides
    @Singleton
    fun provideContactsSender(@ApplicationContext context: Context): ContactsRepository {
        return ContactsRepositoryImpl(context)
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideContactsAppFunctionFactory(
        functions: ContactsAppFunctions,
    ): AppFunctionFactoryRegistration = AppFunctionFactoryRegistration { builder ->
        builder.addEnclosingClassFactory(ContactsAppFunctions::class.java) { functions }
    }

}
