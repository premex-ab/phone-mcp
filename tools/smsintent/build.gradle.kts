plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.smsintent"
}

kotlin {
    jvmToolchain(21)
}


