package se.premex.mcp.location.appfunctions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import se.premex.mcp.location.repositories.LocationInfo
import se.premex.mcp.location.repositories.LocationRepository

class LocationAppFunctionsTest {
    @Test
    fun executeGetCurrentLocation_mapsRepositoryResult() = runBlocking {
        val repository = object : LocationRepository {
            override suspend fun getCurrentLocation() = LocationInfo(
                latitude = 59.3293,
                longitude = 18.0686,
                accuracyMeters = 4.5f,
                altitudeMeters = 28.0,
                speedMetersPerSecond = 1.2f,
                timestampMillis = 1_725_000_000_000,
                provider = "gps",
            )
        }

        val result = LocationAppFunctions(repository).executeGetCurrentLocation()

        assertEquals(59.3293, result.latitude, 0.0)
        assertEquals(18.0686, result.longitude, 0.0)
        assertEquals(4.5f, result.accuracyMeters)
        assertEquals("gps", result.provider)
    }
}
