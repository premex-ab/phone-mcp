package se.premex.mcp.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Repository that handles authentication for the MCP server.
 * Uses a persistent 6-digit authentication token for validation
 * that is stored across app restarts using DataStore.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val context: Context
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_settings")
        private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    }

    // Retrieve the token from DataStore if it exists, or generate a new one
    private val authToken: String = runBlocking {
        val savedToken = context.dataStore.data.map { preferences ->
            preferences[AUTH_TOKEN_KEY] ?: generateAndSaveNewToken()
        }.first()

        savedToken
    }

    // Connection instructions with the current token
    private val connectionInstructions: String = "Please use the token '${authToken}' to authenticate your connection."

    /**
     * Generates a random 6-digit token and saves it to DataStore
     */
    private suspend fun generateAndSaveNewToken(): String {
        val newToken = Random.nextInt(100000, 1000000).toString()
        context.dataStore.edit { preferences ->
            preferences[AUTH_TOKEN_KEY] = newToken
        }
        return newToken
    }

    /**
     * Generates a new token and saves it to DataStore.
     * This can be called manually to force a new token.
     */
    suspend fun refreshToken(): String {
        val newToken = generateAndSaveNewToken()
        return newToken
    }

    /**
     * Authentication result containing user ID and authentication status
     */
    data class AuthResult(
        val isAuthenticated: Boolean,
        val userId: String? = null,
        val message: String = ""
    )

    /**
     * Validates a bearer token and returns an authentication result.
     *
     * @param token The bearer token to validate
     * @return AuthResult containing authentication status and user ID if successful
     */
    fun validateBearerToken(token: String?): AuthResult {
        return when {
            token == null ->
                AuthResult(false, message = "No authentication token provided")
            token == authToken ->
                AuthResult(true, UUID.randomUUID().toString(), "Authentication successful")
            else ->
                AuthResult(false, message = "Invalid authentication token")
        }
    }

    /**
     * Returns the connection instructions showing the required token.
     *
     * @return Instructions string containing the authentication token
     */
    fun getConnectionInstructions(): String {
        return connectionInstructions
    }
}
