package se.premex.mcp.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the tunnel's live connection state from the service (which owns the
 * [TunnelClient]) to the UI. `null` = no tunnel running, `false` = trying to
 * reach the relay, `true` = connected.
 */
@Singleton
class TunnelStatusRepository @Inject constructor() {
    private val _connected = MutableStateFlow<Boolean?>(null)
    val connected: StateFlow<Boolean?> = _connected.asStateFlow()

    fun update(state: Boolean?) {
        _connected.value = state
    }
}
