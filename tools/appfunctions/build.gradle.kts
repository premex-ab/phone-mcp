plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.appfunctions"
}

dependencies {
    implementation(libs.androidx.appfunctions)
}
