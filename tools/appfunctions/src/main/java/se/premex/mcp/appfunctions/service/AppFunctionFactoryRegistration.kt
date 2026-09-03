package se.premex.mcp.appfunctions.service

import androidx.appfunctions.service.AppFunctionConfiguration

/**
 * A flavor-aware contribution to the application's AppFunctions factory registry.
 *
 * Tool modules contribute implementations through Hilt. This avoids referencing the
 * direct-SMS module from Play builds, where that module is intentionally absent.
 */
fun interface AppFunctionFactoryRegistration {
    fun register(builder: AppFunctionConfiguration.Builder)
}
