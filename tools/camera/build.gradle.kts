plugins {
    alias(libs.plugins.mcp.android.tool)
    alias(libs.plugins.mcp.android.appfunctions)
}

android {
    namespace = "se.premex.mcp.camera"
}


dependencies {
    implementation(project(":tools:appfunctions"))

    // Lifecycle process for lifecycle-aware operations
    implementation(libs.androidx.lifecycle.process)

    // CameraX dependencies
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
