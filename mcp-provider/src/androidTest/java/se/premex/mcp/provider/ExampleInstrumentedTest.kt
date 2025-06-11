<<<<<<<< HEAD:mcp-provider/src/androidTest/java/se/premex/mcp/provider/ExampleInstrumentedTest.kt
package se.premex.mcp.provider
========
package se.premex.mcp
>>>>>>>> 77eca3c (Refactor package structure and update build configuration for MCP):app/src/androidTest/java/se/premex/mcp/ExampleInstrumentedTest.kt

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("se.premex.mcp.provider.test", appContext.packageName)
    }
}