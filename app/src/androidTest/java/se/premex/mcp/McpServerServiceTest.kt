package se.premex.mcp

import android.Manifest
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import se.premex.mcp.data.ServerPreferencesRepository
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class McpServerServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun bootDoesNotRequestServiceStartEvenWhenServerWasRunning() {
        val scope = CoroutineScope(Dispatchers.IO)
        val preferences = ServerPreferencesRepository(context, scope)
        val original = runBlocking { preferences.serverShouldRun().first() }
        var startRequests = 0
        val bootContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                startRequests++
                return null
            }

            override fun startService(service: Intent): ComponentName? {
                startRequests++
                return null
            }
        }
        try {
            preferences.setServerShouldRun(true)
            runBlocking {
                withTimeout(5_000) { preferences.serverShouldRun().first { it } }
            }
            instrumentation.runOnMainSync {
                BootReceiver().onReceive(bootContext, Intent(Intent.ACTION_BOOT_COMPLETED))
            }
            assertEquals("Boot must use the restart notification", 0, startRequests)
            assertTrue(runBlocking { preferences.serverShouldRun().first() })
        } finally {
            preferences.setServerShouldRun(original)
            runBlocking {
                withTimeout(5_000) { preferences.serverShouldRun().first { it == original } }
            }
            scope.cancel()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun packagedServiceUsesSpecialUseWithoutDataSyncPermission() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, McpServerService::class.java), 0
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            info.foregroundServiceType
        )
        assertFalse(info.exported)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
        )
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        )
    }

    @Test
    fun serverSurvivesRepeatedStartAndActivityGoingIntoBackground() {
        val intent = Intent(context, McpServerService::class.java)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { ContextCompat.startForegroundService(it, intent) }
                awaitRunning(true)
                awaitEndpoint()
                scenario.onActivity { ContextCompat.startForegroundService(it, intent) }
                scenario.moveToState(Lifecycle.State.CREATED)
                SystemClock.sleep(1500)
                awaitRunning(true)
                awaitEndpoint()
                context.stopService(intent)
                awaitRunning(false)
            }
        } finally {
            context.stopService(intent)
        }
    }

    private fun awaitRunning(expected: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var running = !expected
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { running = McpServerService.isRunning.value }
            if (running == expected) return
            SystemClock.sleep(100)
        }
        assertEquals("Service running state", expected, running)
    }

    private fun awaitEndpoint() {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val connection = URL("http://127.0.0.1:3001/sse").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 500
                connection.readTimeout = 500
                // A live server must reject unauthenticated requests.
                if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) return
            } catch (_: java.io.IOException) {
                // The server may still be initializing.
            } finally {
                connection.disconnect()
            }
            SystemClock.sleep(100)
        }
        assertTrue("Authenticated MCP endpoint did not become available", false)
    }
}
