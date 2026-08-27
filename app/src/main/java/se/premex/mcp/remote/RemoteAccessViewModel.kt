package se.premex.mcp.remote

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RemoteAccessViewModel @Inject constructor(
    private val repository: RemoteAccessRepository,
    tunnelStatusRepository: TunnelStatusRepository,
) : ViewModel() {

    val config: StateFlow<RemoteAccessConfig> = repository.config()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemoteAccessConfig())

    /** Live tunnel state: null = not running, false = connecting, true = connected. */
    val tunnelConnected: StateFlow<Boolean?> = tunnelStatusRepository.connected

    var pairingCode by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            error = null
            if (!enabled) {
                repository.setEnabled(false)
                pairingCode = null
                return@launch
            }
            busy = true
            try {
                val current = repository.config().first()
                if (!current.registered) {
                    val (deviceId, deviceSecret) = withContext(Dispatchers.IO) {
                        RelayApi.registerDevice(current.relayUrl, Build.MODEL)
                    }
                    repository.saveDevice(deviceId, deviceSecret)
                }
                repository.setEnabled(true)
            } catch (e: Exception) {
                error = e.message ?: "Could not reach the relay"
            } finally {
                busy = false
            }
        }
    }

    fun requestPairingCode() {
        viewModelScope.launch {
            error = null
            busy = true
            try {
                val current = repository.config().first()
                val deviceId = current.deviceId
                val deviceSecret = current.deviceSecret
                if (deviceId != null && deviceSecret != null) {
                    pairingCode = withContext(Dispatchers.IO) {
                        RelayApi.requestPairingCode(current.relayUrl, deviceId, deviceSecret)
                    }.first
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not reach the relay"
            } finally {
                busy = false
            }
        }
    }
}
