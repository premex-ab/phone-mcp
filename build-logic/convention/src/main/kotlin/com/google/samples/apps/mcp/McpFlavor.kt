package com.google.samples.apps.mcp

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import org.gradle.kotlin.dsl.invoke

@Suppress("EnumEntryName")
enum class FlavorDimension {
    distribution
}

@Suppress("EnumEntryName")
enum class McpFlavor(val dimension: FlavorDimension, val applicationIdSuffix: String? = null) {
    play(FlavorDimension.distribution),
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
