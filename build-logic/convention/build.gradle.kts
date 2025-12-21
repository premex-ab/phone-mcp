import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.android.lint)
}

group = "com.google.samples.apps.mcp.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradleApiPlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.compose.gradlePlugin)
    implementation(libs.firebase.crashlytics.gradlePlugin)
    implementation(libs.firebase.performance.gradlePlugin)
    implementation(libs.google.services.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    implementation(libs.truth)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = libs.plugins.mcp.android.application.compose.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.mcp.android.application.asProvider().get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationJacoco") {
            id = libs.plugins.mcp.android.application.jacoco.get().pluginId
            implementationClass = "AndroidApplicationJacocoConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.mcp.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.mcp.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeatureImpl") {
            id = libs.plugins.mcp.android.feature.impl.get().pluginId
            implementationClass = "AndroidFeatureImplConventionPlugin"
        }
        register("androidFeatureApi") {
            id = libs.plugins.mcp.android.feature.api.get().pluginId
            implementationClass = "AndroidFeatureApiConventionPlugin"
        }
        register("androidLibraryJacoco") {
            id = libs.plugins.mcp.android.library.jacoco.get().pluginId
            implementationClass = "AndroidLibraryJacocoConventionPlugin"
        }
        register("androidTest") {
            id = libs.plugins.mcp.android.test.get().pluginId
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.mcp.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("androidFirebase") {
            id = libs.plugins.mcp.android.application.firebase.get().pluginId
            implementationClass = "AndroidApplicationFirebaseConventionPlugin"
        }
        register("androidFlavors") {
            id = libs.plugins.mcp.android.application.flavors.get().pluginId
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLint") {
            id = libs.plugins.mcp.android.lint.get().pluginId
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.mcp.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("root") {
            id = libs.plugins.mcp.root.get().pluginId
            implementationClass = "RootPlugin"
        }
    }
}
