package se.premex.mcp.location.repositories

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long,
    val provider: String?
) {
    override fun toString(): String {
        return """
            Latitude: $latitude
            Longitude: $longitude
            Accuracy (m): ${accuracyMeters ?: "unknown"}
            Altitude (m): ${altitudeMeters ?: "unknown"}
            Speed (m/s): ${speedMetersPerSecond ?: "unknown"}
            Timestamp (epoch ms): $timestampMillis
            Provider: ${provider ?: "unknown"}
        """.trimIndent()
    }
}
