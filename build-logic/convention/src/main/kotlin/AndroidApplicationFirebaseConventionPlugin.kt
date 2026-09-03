import com.google.samples.apps.mcp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Add Firebase dependencies - available for all builds
            // The Google Services plugin is applied conditionally in app/build.gradle.kts
            dependencies {
                val bom = libs.findLibrary("com-google-firebase-firebase-bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("firebase-analytics").get())
                // Note: do NOT exclude protobuf-javalite here. The exclusion in the
                // Now in Android template (which this plugin was based on) assumes a
                // datastore-proto module that provides protobuf classes; this project
                // has none, so excluding protobuf strips Firebase Performance's
                // runtime dependency and crashes the app at startup.
                "implementation"(libs.findLibrary("firebase-performance").get())
            }
        }
    }
}
