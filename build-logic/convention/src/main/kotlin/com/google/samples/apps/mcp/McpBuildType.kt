package com.google.samples.apps.mcp

enum class McpBuildType(val applicationIdSuffix: String? = null) {
    DEBUG(".debug"),
    RELEASE,
}
