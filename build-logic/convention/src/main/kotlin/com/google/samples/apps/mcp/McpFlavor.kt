package com.google.samples.apps.mcp

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    distribution
}

@Suppress("EnumEntryName")
enum class McpFlavor(
    val dimension: FlavorDimension,
    val applicationIdSuffix: String? = null,
    /**
     * Remote access (phonemcp.ai) is Play-exclusive: its subscription can only
     * be sold through Play Billing, and the hosted relay is a running cost —
     * sideloaded builds are local-network only.
     */
    val remoteAccess: Boolean = false,
) {
    play(FlavorDimension.distribution, remoteAccess = true),
    full(FlavorDimension.distribution, applicationIdSuffix = ".full"),
}

fun configureFlavors(
    commonExtension: CommonExtension,
    flavorConfigurationBlock: ProductFlavor.(flavor: McpFlavor) -> Unit = {},
) {
    commonExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }

        McpFlavor.entries.forEach { mcpFlavor ->
            productFlavors.register(mcpFlavor.name) {
                dimension = mcpFlavor.dimension.name
                buildConfigField("boolean", "REMOTE_ACCESS", mcpFlavor.remoteAccess.toString())
                flavorConfigurationBlock(this, mcpFlavor)
                if (commonExtension is ApplicationExtension && this is ApplicationProductFlavor) {
                    if (mcpFlavor.applicationIdSuffix != null) {
                        applicationIdSuffix = mcpFlavor.applicationIdSuffix
                    }
                }
            }
        }
    }
}
