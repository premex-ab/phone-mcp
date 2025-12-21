import com.google.samples.apps.mcp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude

class AndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Add Firebase dependencies - available for all builds
            // The Google Services plugin is applied conditionally in app/build.gradle.kts
            dependencies {
                val bom = libs.findLibrary("com-google-firebase-firebase-bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("firebase-analytics").get())
                "implementation"(libs.findLibrary("firebase-performance").get()) {
                    /*
                    Exclusion of protobuf / protolite dependencies is necessary as the
                    datastore-proto brings in protobuf dependencies. These are the source of truth
                    for Now in Android.
                    That's why the duplicate classes from below dependencies are excluded.
                    */
                    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
                    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
                }
            }
        }
    }
}
