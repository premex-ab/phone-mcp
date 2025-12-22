plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.contacts"
}

kotlin {
    jvmToolchain(21)
}

