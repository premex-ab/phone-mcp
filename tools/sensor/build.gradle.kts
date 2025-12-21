plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "se.premex.mcp.sensor"
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
    ksp(libs.hilt.compiler)

    // Module dependencies
    implementation(project(":core"))

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

