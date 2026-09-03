plugins {
    alias(libs.plugins.mcp.android.tool)
    alias(libs.plugins.mcp.android.appfunctions)
}

dependencies {
    implementation(project(":tools:appfunctions"))
}

android {
    namespace = "se.premex.mcp.files"
}
