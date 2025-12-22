package com.google.samples.apps.mcp

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    contentType
}

@Suppress("EnumEntryName")
enum class McpFlavor(val dimension: FlavorDimension, val applicationIdSuffix: String? = null) {
    demo(FlavorDimension.contentType, applicationIdSuffix = ".demo"),
    prod(FlavorDimension.contentType),
}

fun configureFlavors(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
    flavorConfigurationBlock: ProductFlavor.(flavor: McpFlavor) -> Unit = {},
) {
    commonExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }

        productFlavors {
            McpFlavor.entries.forEach { mcpFlavor ->
                register(mcpFlavor.name) {
                    dimension = mcpFlavor.dimension.name
                    flavorConfigurationBlock(this, mcpFlavor)
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        if (mcpFlavor.applicationIdSuffix != null) {
                            applicationIdSuffix = mcpFlavor.applicationIdSuffix
                        }
                    }
                }
            }
        }
    }
}
