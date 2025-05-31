package se.premex.adserver.mcp.ads

import kotlinx.serialization.Serializable

// Banner data model matching the API response, might need it later for deserialization
@Suppress("unused")
@Serializable
data class BannerResponse(
    val id: String,
    val name: String,
    val content: String,
    val url: String,
    val clickUrl: String,
    val impressions: Int
)