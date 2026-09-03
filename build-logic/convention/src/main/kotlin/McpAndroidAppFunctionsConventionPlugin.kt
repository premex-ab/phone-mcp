import com.google.devtools.ksp.gradle.KspExtension
import com.google.samples.apps.mcp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds Android AppFunctions publishing support to an application or library module.
 *
 * Library modules compile their annotated functions into contribution metadata. The
 * application module additionally aggregates all contributions into the generated
 * AppFunction service and metadata packaged in the APK.
 */
class McpAndroidAppFunctionsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.google.devtools.ksp")

            dependencies {
                "implementation"(libs.findLibrary("androidx.appfunctions").get())
                "implementation"(libs.findLibrary("androidx.appfunctions.service").get())
                "ksp"(libs.findLibrary("androidx.appfunctions.compiler").get())
            }

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<KspExtension> {
                    arg("appfunctions:aggregateAppFunctions", "true")
                }
            }
        }
    }
}
