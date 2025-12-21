plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.sensor"
}

kotlin {
    jvmToolchain(21)
}


