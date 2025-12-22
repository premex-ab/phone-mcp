plugins {
    alias(libs.plugins.mcp.android.application)
    alias(libs.plugins.mcp.android.application.compose)
}

android {
    namespace = "se.premex.externalmcptool"

    defaultConfig {
        applicationId = "se.premex.externalmcptool"
        versionCode = 1
        versionName = "1.0"
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":mcp-provider"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.ui.tooling)
}