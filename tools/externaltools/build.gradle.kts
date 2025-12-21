@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.externaltools"
}

kotlin {
    jvmToolchain(21)
}

