import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.google.samples.apps.mcp.configureJacoco
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "jacoco")

            val androidExtension = extensions.getByType<ApplicationExtension>()

            // Coverage instrumentation forces the variant to be debuggable, so
            // it must never be enabled for release builds — Play rejects
            // debuggable artifacts.
            androidExtension.buildTypes.configureEach {
                if (name == "debug") {
                    enableAndroidTestCoverage = true
                    enableUnitTestCoverage = true
                }
            }

            configureJacoco(extensions.getByType<ApplicationAndroidComponentsExtension>())
        }
    }
}
