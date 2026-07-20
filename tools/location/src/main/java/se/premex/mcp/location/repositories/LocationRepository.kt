package se.premex.mcp.location.repositories

interface LocationRepository {
    suspend fun getCurrentLocation(): LocationInfo?
}
