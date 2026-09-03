plugins {
    alias(libs.plugins.mcp.android.tool)
    alias(libs.plugins.mcp.android.appfunctions)
}

android {
    namespace = "se.premex.mcp.sms"
}

dependencies {
    implementation(project(":tools:appfunctions"))
    implementation(libs.kotlinx.coroutines.android)
}
