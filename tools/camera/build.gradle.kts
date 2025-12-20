plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "se.premex.mcp.camera"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.lifecycle.process)
    ksp(libs.hilt.compiler)

    // Module dependencies
    implementation(project(":core"))

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    api(libs.io.modelcontextprotocol.kotlin.sdk)
    implementation(libs.io.ktor.ktor.client.core)
    implementation(libs.io.ktor.ktor.client.cio)
    implementation(libs.io.ktor.ktor.client.content.negotiation)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
