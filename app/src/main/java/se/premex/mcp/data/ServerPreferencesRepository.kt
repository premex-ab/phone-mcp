package se.premex.mcp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    companion object {
        private val HOST_KEY = stringPreferencesKey("server_host")
        private val PORT_KEY = intPreferencesKey("server_port")
    }
}