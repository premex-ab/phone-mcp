package se.premex.mcp.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteAccessConfig(
    val enabled: Boolean = false,
    val deviceId: String? = null,
    val deviceSecret: String? = null,
) {
    val registered: Boolean get() = deviceId != null && deviceSecret != null

    /** The PhoneMCP relay. Not user-configurable — the hosted relay is the product. */
    val relayUrl: String get() = RELAY_URL

    companion object {
        const val RELAY_URL = "https://phonemcp.ai"
    }
}

/** Persists the Remote access (phonemcp.ai tunnel) settings. */
@Singleton
class RemoteAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remote_access")
        private val ENABLED = booleanPreferencesKey("enabled")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val DEVICE_SECRET = stringPreferencesKey("device_secret")
    }

    fun config(): Flow<RemoteAccessConfig> = context.dataStore.data.map { preferences ->
        RemoteAccessConfig(
            enabled = preferences[ENABLED] ?: false,
            deviceId = preferences[DEVICE_ID],
            deviceSecret = preferences[DEVICE_SECRET],
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun saveDevice(deviceId: String, deviceSecret: String) {
        context.dataStore.edit {
            it[DEVICE_ID] = deviceId
            it[DEVICE_SECRET] = deviceSecret
        }
    }
}
