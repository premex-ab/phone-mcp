import com.google.samples.apps.mcp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for MCP Tool modules.
 *
 * Applies standard configuration for all tool libraries:
 * - Android library setup (via mcp.android.library)
 * - Hilt dependency injection
 * - Common MCP SDK and Ktor dependencies
 * - Standard testing dependencies
 */
class McpAndroidToolConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply base library configuration (compileSdk, minSdk, jvmToolchain, flavors, etc.)
            apply(plugin = "mcp.android.library")
            apply(plugin = "mcp.hilt")


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

