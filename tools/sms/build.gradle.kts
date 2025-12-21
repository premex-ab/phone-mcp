plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.sms"
}

kotlin {
    jvmToolchain(21)
}


