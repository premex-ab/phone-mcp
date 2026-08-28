package se.premex.mcp.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val billingManager: BillingManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** The remote-access subscription; null when Play/billing is unavailable. */
    val subscriptionProduct = billingManager.productDetails

    init {
        billingManager.connect()
        // Any purchase (new, or restored after reinstall) is sent to the relay
        // for verification; only then is it acknowledged towards Play.
        viewModelScope.launch {
            billingManager.pendingPurchase.collect { purchase ->
                if (purchase != null) submitPurchase(purchase)
            }
        }
    }

    fun subscribe(activity: android.app.Activity, basePlanId: String? = null) {
        if (!billingManager.launchPurchase(activity, basePlanId)) {
            error = "Google Play billing is not available on this device"
        }
    }

    private suspend fun submitPurchase(purchase: com.android.billingclient.api.Purchase) {
        val current = repository.config().first()
        val deviceId = current.deviceId ?: return
        val deviceSecret = current.deviceSecret ?: return
        try {
            entitlement = withContext(Dispatchers.IO) {
                RelayApi.submitPurchase(current.relayUrl, deviceId, deviceSecret, purchase.purchaseToken)
            }
            billingManager.acknowledge(purchase)
            billingManager.clearPending()
        } catch (e: Exception) {
            // Left pending: retried on next app start via queryExistingPurchases
            error = e.message ?: "Could not verify the purchase"
        }
    }

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
    var pairedClients by mutableStateOf<List<PairedClientInfo>>(emptyList())
        private set

    /** "trial"/"paid"/"grace"/"expired" to activeUntil ISO instant; null until fetched. */
    var entitlement by mutableStateOf<Pair<String, String>?>(null)
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
                        RelayApi.registerDevice(current.relayUrl, Build.MODEL, trialAnchor())
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

    /**
     * Hash of a device-stable identifier: survives clear-data and reinstall,
     * so the relay can anchor the free trial to the physical phone. Used only
     * for trial-abuse prevention; only the hash ever leaves the device.
     */
    private fun trialAnchor(): String? = runCatching {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Best-effort refresh of the paired clients list; keeps the old list on failure. */
    fun refreshPairedClients() {
        viewModelScope.launch {
            val current = repository.config().first()
            val deviceId = current.deviceId ?: return@launch
            val deviceSecret = current.deviceSecret ?: return@launch
            try {
                pairedClients = withContext(Dispatchers.IO) {
                    RelayApi.listClients(current.relayUrl, deviceId, deviceSecret)
                }
            } catch (_: Exception) {
                // informational section — stale data beats an error banner
            }
            try {
                entitlement = withContext(Dispatchers.IO) {
                    RelayApi.getEntitlement(current.relayUrl, deviceId, deviceSecret)
                }
            } catch (_: Exception) {
                // same: informational
            }
        }
    }

    fun revokeClient(clientId: String) {
        viewModelScope.launch {
            val current = repository.config().first()
            val deviceId = current.deviceId ?: return@launch
            val deviceSecret = current.deviceSecret ?: return@launch
            error = null
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    RelayApi.revokeClient(current.relayUrl, deviceId, deviceSecret, clientId)
                }
                pairedClients = pairedClients.filterNot { it.clientId == clientId }
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
