plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.ads"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    jvmToolchain(21)
}


