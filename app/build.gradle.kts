
import com.android.tools.r8.internal.`in`

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "se.premex.mcp"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.premex.mcp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    // Import the BoM for the Firebase platform
    implementation(platform(libs.com.google.firebase.firebase.bom))

    // Add the dependency for the Analytics library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.firebase.analytics)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Module dependencies
    implementation(project(":core"))
    //implementation(project(":ads-mcp-extensions"))

    // In-App Update
    //implementation(libs.se.warting.inapp.update.compose.mui)

    // External dependencies
    implementation(libs.io.modelcontextprotocol.kotlin.sdk)
    implementation(libs.org.slf4j.slf4j.nop)

    // Ktor dependencies
    implementation(libs.io.ktor.ktor.server.core)
    implementation(libs.io.ktor.ktor.server.cio)
    implementation(libs.io.ktor.ktor.server.sse)
    implementation(libs.io.ktor.ktor.server.auth)
    implementation(libs.io.ktor.ktor.server.cors)
    implementation(libs.io.ktor.ktor.client.content.negotiation)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json)
    implementation(project(":tools:sms"))
    implementation(project(":tools:ads"))
    implementation(project(":tools:contacts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
