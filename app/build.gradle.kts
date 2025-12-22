import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.mcp.android.application)
    alias(libs.plugins.mcp.android.application.compose)
    alias(libs.plugins.mcp.android.application.flavors)
    alias(libs.plugins.mcp.android.application.jacoco)
    alias(libs.plugins.mcp.android.application.firebase)
    alias(libs.plugins.mcp.hilt)
    alias(libs.plugins.play.publisher)
}

// Apply Firebase plugins conditionally based on gradle property
// Usage: ./gradlew build -PenableFirebase=true
val enableFirebase = project.findProperty("enableFirebase")?.toString()?.toBoolean() ?: false
if (enableFirebase) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.firebase-perf")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Get version from gradle property or fallback to default
val appVersionName = project.findProperty("versionName")?.toString() ?: "0.4.0"
val appVersionCode = (project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 5)

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
        versionCode = appVersionCode
        versionName = appVersionName

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
}

dependencies {

    // Import the BoM for the Firebase platform
    implementation(platform(libs.com.google.firebase.firebase.bom))

    // Add the dependency for the Analytics library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.firebase.analytics)

    // DataStore for persisting authentication token
    implementation(libs.androidx.datastore.preferences)

    // Hilt navigation
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
    implementation(libs.androidx.compose.material.icons.extended)
    ksp(libs.kotlin.metadata.jvm)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
