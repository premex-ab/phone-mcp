import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.play.publisher)
}

// Get version from gradle property or fallback to default
val versionNameName = project.findProperty("versionNameName")?.toString() ?: "0.4.0"
val versionNameCode = (project.findProperty("versionNameCode")?.toString()?.toIntOrNull() ?: 5)

// Configure Play Store publishing only if credentials file exists
val playCredentialsFile = file("byggappen-dev-b151ea2d4990.json")
if (playCredentialsFile.exists()) {
    play {
        serviceAccountCredentials.set(playCredentialsFile)
        defaultToAppBundles.set(true)
    }
}

// Load keystore properties for release builds (optional for debug)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "se.premex.mcp"
    compileSdk = 36

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("config") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "se.premex.mcp"
        minSdk = 26
        targetSdk = 36
        versionCode = versionNameCode
        versionName = versionNameName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug builds work without google-services.json
            // Firebase will be disabled if google-services.json is missing
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("config")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {

    // Import the BoM for the Firebase platform
    implementation(platform(libs.com.google.firebase.firebase.bom))

    // Add the dependency for the Analytics library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.firebase.analytics)

    // DataStore for persisting authentication token
    implementation(libs.androidx.datastore.preferences)


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
    //implementation(project(":tools:sms"))
    implementation(project(":tools:smsintent"))
    implementation(project(":tools:ads"))
    implementation(project(":tools:contacts"))
    implementation(project(":tools:sensor"))
    implementation(project(":tools:camera"))
    implementation(project(":tools:externaltools"))
    implementation(project(":mcp-provider"))

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
