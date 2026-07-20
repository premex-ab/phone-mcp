package se.premex.mcp.location.repositories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationRepositoryImpl(
    private val context: Context
) : LocationRepository {

    override suspend fun getCurrentLocation(): LocationInfo? {
        if (!hasLocationPermission()) {
            throw SecurityException("Location permission not granted")
        }

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { locationManager.allProviders.contains(it) }

        // Ask for a fresh fix where the platform supports it, falling back to
        // the most recent cached position from any provider
        val freshLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            providers.firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(FRESH_LOCATION_TIMEOUT_MS) {
                    requestCurrentLocation(locationManager, provider)
                }
            }
        } else {
            null
        }

        val location = freshLocation ?: providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    // Permission is verified at the top of this method; treat a
                    // mid-flight revocation as no position available
                    null
                }
            }
            .maxByOrNull { it.time }

        return location?.let {
            LocationInfo(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                altitudeMeters = if (it.hasAltitude()) it.altitude else null,
                speedMetersPerSecond = if (it.hasSpeed()) it.speed else null,
                timestampMillis = it.time,
                provider = it.provider
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestCurrentLocation(
        locationManager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        try {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                context.mainExecutor
            ) { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }
        } catch (e: SecurityException) {
            // Surface missing permission to the caller instead of hanging
            if (continuation.isActive) {
                continuation.cancel(e)
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val FRESH_LOCATION_TIMEOUT_MS = 10_000L
    }
}
