package se.premex.mcp.location.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import androidx.core.content.ContextCompat
import se.premex.mcp.location.repositories.LocationInfo
import se.premex.mcp.location.repositories.LocationRepository
import javax.inject.Inject

/** A geographic position returned by the phone's location providers. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFunctionLocation(
    /** Latitude in decimal degrees. */
    val latitude: Double,
    /** Longitude in decimal degrees. */
    val longitude: Double,
    /** Estimated horizontal accuracy in meters, when available. */
    val accuracyMeters: Float?,
    /** Altitude above sea level in meters, when available. */
    val altitudeMeters: Double?,
    /** Current speed in meters per second, when available. */
    val speedMetersPerSecond: Float?,
    /** Time of the fix as Unix epoch milliseconds. */
    val timestampMillis: Long,
    /** Android provider that supplied the position, when available. */
    val provider: String?,
)

/** Phone actions that retrieve the device's current location. */
class LocationAppFunctions @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    /**
     * Get the phone's freshest available geographic position.
     *
     * Android attempts a fresh GPS or network fix, then falls back to the most recent cached
     * position. The result can contain accuracy, altitude, speed, timestamp, and provider data.
     *
     * @param appFunctionContext The Android execution context used to verify location permission.
     * @return The freshest available device position.
     * @throws AppFunctionPermissionRequiredException If location permission has not been granted.
     * @throws AppFunctionAppUnknownException If Android cannot obtain a position.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun getCurrentLocation(
        appFunctionContext: AppFunctionContext,
    ): AppFunctionLocation {
        val context = appFunctionContext.context
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            throw AppFunctionPermissionRequiredException(
                "Location permission is required. Ask the user to enable Location and grant permission.",
            )
        }

        return try {
            executeGetCurrentLocation()
        } catch (exception: SecurityException) {
            throw AppFunctionPermissionRequiredException(
                "Location permission was revoked. Ask the user to grant it and retry.",
            )
        }
    }

    internal suspend fun executeGetCurrentLocation(): AppFunctionLocation {
        return locationRepository.getCurrentLocation()?.toAppFunctionLocation()
            ?: throw AppFunctionAppUnknownException(
                "Location is unavailable. Ask the user to enable location services and retry.",
            )
    }
}

private fun LocationInfo.toAppFunctionLocation() = AppFunctionLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    timestampMillis = timestampMillis,
    provider = provider,
)
