package se.premex.mcp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.premex.mcp.di.AppCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_preferences"
)

/**
 * Data class for server configuration
 */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 3001
)

/**
 * Repository for managing server preferences (host, port) with DataStore
 */
@Singleton
class ServerPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope
) {
    private val dataStore = context.serverPreferencesDataStore

    /**
     * Get the saved server configuration as a Flow
     */
    fun getServerConfig(): Flow<ServerConfig> {
        return dataStore.data.map { preferences ->
            ServerConfig(
                host = preferences[HOST_KEY] ?: "0.0.0.0",
                port = preferences[PORT_KEY] ?: 3001
            )
        }
    }

    /**
     * Update both host and port at once
     */
    fun updateServerConfig(host: String, port: Int) {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences[HOST_KEY] = host
                preferences[PORT_KEY] = port
            }
        }
    }

    /**
     * Whether the first-run onboarding has been completed
     */
    fun isOnboardingCompleted(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] ?: false
        }
    }

    fun setOnboardingCompleted() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences[ONBOARDING_COMPLETED_KEY] = true
            }
        }
    }

    /**
     * Whether an MCP client has ever successfully connected to the server
     */
    fun hasClientConnected(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[CLIENT_CONNECTED_KEY] ?: false
        }
    }

    fun markClientConnected() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences[CLIENT_CONNECTED_KEY] = true
            }
        }
    }

    /**
     * Whether the in-app review flow has already been requested
     */
    fun isReviewPrompted(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[REVIEW_PROMPTED_KEY] ?: false
        }
    }

    fun markReviewPrompted() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences[REVIEW_PROMPTED_KEY] = true
            }
        }
    }

    /**
     * The user's intent: true between "start server" and "stop server".
     * Read by [se.premex.mcp.BootReceiver] to bring the server back after a
     * phone reboot — a remote-access phone must not go dark because Android
     * restarted overnight.
     */
    fun serverShouldRun(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[SERVER_SHOULD_RUN_KEY] ?: false
        }
    }

    fun setServerShouldRun(shouldRun: Boolean) {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences[SERVER_SHOULD_RUN_KEY] = shouldRun
            }
        }
    }

    /** Whether the AppFunctions discovery card is shown on the home screen. */
    fun isAppFunctionsDiscoveryVisible(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[APP_FUNCTIONS_DISCOVERY_VISIBLE_KEY] ?: true
        }
    }

    fun setAppFunctionsDiscoveryVisible(isVisible: Boolean) {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences[APP_FUNCTIONS_DISCOVERY_VISIBLE_KEY] = isVisible
            }
        }
    }

    companion object {
        private val HOST_KEY = stringPreferencesKey("server_host")
        private val PORT_KEY = intPreferencesKey("server_port")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val CLIENT_CONNECTED_KEY = booleanPreferencesKey("client_connected")
        private val REVIEW_PROMPTED_KEY = booleanPreferencesKey("review_prompted")
        private val SERVER_SHOULD_RUN_KEY = booleanPreferencesKey("server_should_run")
        private val APP_FUNCTIONS_DISCOVERY_VISIBLE_KEY =
            booleanPreferencesKey("appfunctions_discovery_visible")
    }
}
