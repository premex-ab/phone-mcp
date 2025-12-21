import com.android.build.api.dsl.LibraryExtension
import com.google.samples.apps.mcp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention plugin for MCP Tool modules.
 *
 * Applies standard configuration for all tool libraries:
 * - Android library setup
 * - Kotlin and KSP plugins
 * - Hilt dependency injection
 * - Common MCP SDK and Ktor dependencies
 * - Standard testing dependencies
 */
class McpAndroidToolConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "org.jetbrains.kotlin.android")
            apply(plugin = "mcp.android.lint")
            apply(plugin = "mcp.hilt")

            extensions.configure<LibraryExtension> {
                compileSdk = 36

                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

            // Configure Kotlin JVM toolchain
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }

            dependencies {
                // Module dependencies
                "implementation"(project(":core"))

                // MCP SDK
                "api"(libs.findLibrary("io.modelcontextprotocol.kotlin.sdk").get())

                // Ktor Client
                "implementation"(libs.findLibrary("io.ktor.ktor.client.core").get())
                "implementation"(libs.findLibrary("io.ktor.ktor.client.cio").get())
                "implementation"(libs.findLibrary("io.ktor.ktor.client.content.negotiation").get())
                "implementation"(libs.findLibrary("io.ktor.ktor.serialization.kotlinx.json").get())

                // Android Core
                "implementation"(libs.findLibrary("androidx.core.ktx").get())
                "implementation"(libs.findLibrary("androidx.appcompat").get())
                "implementation"(libs.findLibrary("material").get())

                // Testing
                "testImplementation"(libs.findLibrary("junit").get())
                "androidTestImplementation"(libs.findLibrary("androidx.junit").get())
                "androidTestImplementation"(libs.findLibrary("androidx.espresso.core").get())
            }
        }
    }
}

