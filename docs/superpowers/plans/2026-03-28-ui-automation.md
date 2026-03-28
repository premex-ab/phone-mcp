# UI Automation Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `tools/uiautomation/` module that exposes the full Android UI to AI assistants via an AccessibilityService — enabling screen reading (accessibility tree + screenshots) and interaction (tap, swipe, type, press keys, scroll).

**Architecture:** An `AccessibilityService` runs as a system service and stores itself in a singleton holder. A standard MCP tool module (configurator pattern) registers 10 MCP tools that delegate to the service for screen reading and gesture dispatch. No external dependencies — pure Android framework APIs.

**Tech Stack:** Android AccessibilityService API, GestureDescription API, Kotlin, Hilt DI, MCP Kotlin SDK, kotlinx.serialization

---

## File Map

| File | Responsibility |
|------|---------------|
| `tools/uiautomation/build.gradle.kts` | Module build config using `mcp.android.tool` convention plugin |
| `tools/uiautomation/src/main/AndroidManifest.xml` | AccessibilityService declaration + manifest merge |
| `tools/uiautomation/src/main/res/xml/accessibility_service_config.xml` | Service capabilities (gestures, screenshots, window content) |
| `tools/uiautomation/src/main/res/values/strings.xml` | Service description shown in Android Settings |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/AccessibilityServiceConnection.kt` | Singleton holder for the active service instance |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/PhoneAccessibilityService.kt` | AccessibilityService subclass — lifecycle + delegation |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/ScreenTreeSerializer.kt` | Converts AccessibilityNodeInfo tree to flat JSON |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/GestureHelper.kt` | Builds GestureDescription objects for tap/swipe/long-press |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfigurator.kt` | Configurator interface |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfiguratorImpl.kt` | Registers all 10 MCP tools with the server |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/tool/UiAutomationTool.kt` | McpTool implementation |
| `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/di/UiAutomationToolModule.kt` | Hilt module providing McpTool @IntoSet |
| `settings.gradle.kts` | Add `include(":tools:uiautomation")` |
| `app/build.gradle.kts` | Add `implementation(project(":tools:uiautomation"))` |

---

### Task 1: Module Scaffolding & Build Integration

**Files:**
- Create: `tools/uiautomation/build.gradle.kts`
- Create: `tools/uiautomation/src/main/AndroidManifest.xml`
- Create: `tools/uiautomation/src/main/res/xml/accessibility_service_config.xml`
- Create: `tools/uiautomation/src/main/res/values/strings.xml`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Create build.gradle.kts**

Create `tools/uiautomation/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.uiautomation"
}
```

- [ ] **Step 2: Create AndroidManifest.xml**

Create `tools/uiautomation/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <service
            android:name="se.premex.mcp.uiautomation.service.PhoneAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>

</manifest>
```

- [ ] **Step 3: Create accessibility_service_config.xml**

Create `tools/uiautomation/src/main/res/xml/accessibility_service_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews"
    android:description="@string/accessibility_service_description" />
```

- [ ] **Step 4: Create strings.xml**

Create `tools/uiautomation/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="accessibility_service_description">Allows Phone MCP to read screen content and perform actions on your behalf. Used by AI assistants to interact with apps on your device.</string>
</resources>
```

- [ ] **Step 5: Add module to settings.gradle.kts**

In `settings.gradle.kts`, add after the `include(":tools:camera")` line:

```kotlin
include(":tools:uiautomation")
```

- [ ] **Step 6: Add dependency to app/build.gradle.kts**

In `app/build.gradle.kts`, add after the `implementation(project(":tools:externaltools"))` line:

```kotlin
implementation(project(":tools:uiautomation"))
```

- [ ] **Step 7: Verify build compiles**

Run: `./gradlew :tools:uiautomation:assembleDebug`

This will fail because source files don't exist yet — that's expected. The build config itself should parse without errors. Verify there are no Gradle sync errors by checking the output mentions the module was found.

- [ ] **Step 8: Commit**

```bash
git add tools/uiautomation/build.gradle.kts tools/uiautomation/src/main/AndroidManifest.xml tools/uiautomation/src/main/res/ settings.gradle.kts app/build.gradle.kts
git commit -m "feat(uiautomation): scaffold module with build config, manifest, and resources"
```

---

### Task 2: AccessibilityServiceConnection Singleton

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/AccessibilityServiceConnection.kt`

- [ ] **Step 1: Create AccessibilityServiceConnection**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/AccessibilityServiceConnection.kt`:

```kotlin
package se.premex.mcp.uiautomation.service

import android.accessibilityservice.AccessibilityService
import java.lang.ref.WeakReference

/**
 * Singleton holder for the active PhoneAccessibilityService instance.
 * Tools check [instance] to verify the service is running and to access
 * rootInActiveWindow, performGlobalAction(), dispatchGesture(), and takeScreenshot().
 */
object AccessibilityServiceConnection {

    @Volatile
    private var serviceRef: WeakReference<AccessibilityService>? = null

    val instance: AccessibilityService?
        get() = serviceRef?.get()

    val isConnected: Boolean
        get() = instance != null

    fun onServiceConnected(service: AccessibilityService) {
        serviceRef = WeakReference(service)
    }

    fun onServiceDisconnected() {
        serviceRef = null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/AccessibilityServiceConnection.kt
git commit -m "feat(uiautomation): add AccessibilityServiceConnection singleton"
```

---

### Task 3: PhoneAccessibilityService

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/PhoneAccessibilityService.kt`

- [ ] **Step 1: Create PhoneAccessibilityService**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/PhoneAccessibilityService.kt`:

```kotlin
package se.premex.mcp.uiautomation.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PhoneAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceConnection.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we query the screen on-demand via rootInActiveWindow,
        // rather than reacting to events.
    }

    override fun onInterrupt() {
        // No-op: required override
    }

    override fun onDestroy() {
        super.onDestroy()
        AccessibilityServiceConnection.onServiceDisconnected()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/PhoneAccessibilityService.kt
git commit -m "feat(uiautomation): add PhoneAccessibilityService"
```

---

### Task 4: ScreenTreeSerializer

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/ScreenTreeSerializer.kt`

- [ ] **Step 1: Create ScreenTreeSerializer**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/ScreenTreeSerializer.kt`:

```kotlin
package se.premex.mcp.uiautomation.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ScreenTreeSerializer {

    private const val MAX_DEPTH = 15

    fun serialize(root: AccessibilityNodeInfo?, packageName: String?, activityName: String?): JsonObject {
        return buildJsonObject {
            put("package", packageName ?: "unknown")
            put("activity", activityName ?: "unknown")
            put("nodes", serializeNodes(root))
        }
    }

    private fun serializeNodes(root: AccessibilityNodeInfo?): JsonArray {
        if (root == null) return buildJsonArray {}

        val nodes = mutableListOf<JsonObject>()
        var index = 0
        traverseNode(root, 0, nodes, index) { index++ ; index - 1 }
        return JsonArray(nodes)
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodes: MutableList<JsonObject>,
        currentIndex: Int,
        nextIndex: () -> Int
    ) {
        if (depth > MAX_DEPTH) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Prune invisible nodes
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        // Prune system UI (status bar / nav bar) — they typically sit in
        // com.android.systemui package
        val nodePackage = node.packageName?.toString()
        if (nodePackage == "com.android.systemui") return

        val idx = currentIndex
        val jsonNode = buildJsonObject {
            put("depth", depth)
            put("index", idx)
            put("class", node.className?.toString() ?: "Unknown")
            put("bounds", buildJsonArray {
                add(JsonPrimitive(bounds.left))
                add(JsonPrimitive(bounds.top))
                add(JsonPrimitive(bounds.right))
                add(JsonPrimitive(bounds.bottom))
            })

            // Only include non-empty string fields
            node.text?.toString()?.takeIf { it.isNotEmpty() }?.let { put("text", it) }
            node.hintText?.toString()?.takeIf { it.isNotEmpty() }?.let { put("hint", it) }
            node.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let { put("contentDescription", it) }
            node.viewIdResourceName?.takeIf { it.isNotEmpty() }?.let { put("resourceId", it) }

            // Only include boolean flags when true (except enabled which is omitted when true)
            if (node.isClickable) put("clickable", true)
            if (node.isScrollable) put("scrollable", true)
            if (node.isFocused) put("focused", true)
            if (node.isChecked) put("checked", true)
            if (node.isSelected) put("selected", true)
            if (node.isPassword) put("password", true)
            if (!node.isEnabled) put("enabled", false)
        }
        nodes.add(jsonNode)

        // Traverse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, depth + 1, nodes, nextIndex(), nextIndex)
            child.recycle()
        }
    }

    fun findNodes(root: AccessibilityNodeInfo?, query: String): JsonArray {
        if (root == null) return buildJsonArray {}

        val matches = mutableListOf<JsonObject>()
        findMatchingNodes(root, query.lowercase(), matches)
        return JsonArray(matches)
    }

    private fun findMatchingNodes(
        node: AccessibilityNodeInfo,
        query: String,
        matches: MutableList<JsonObject>
    ) {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val resourceId = node.viewIdResourceName?.lowercase() ?: ""

        if (text.contains(query) || contentDesc.contains(query) || resourceId.contains(query)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerX = (bounds.left + bounds.right) / 2
            val centerY = (bounds.top + bounds.bottom) / 2

            matches.add(buildJsonObject {
                put("class", node.className?.toString() ?: "Unknown")
                node.text?.toString()?.let { put("text", it) }
                node.contentDescription?.toString()?.let { put("contentDescription", it) }
                node.viewIdResourceName?.let { put("resourceId", it) }
                put("bounds", buildJsonArray {
                    add(JsonPrimitive(bounds.left))
                    add(JsonPrimitive(bounds.top))
                    add(JsonPrimitive(bounds.right))
                    add(JsonPrimitive(bounds.bottom))
                })
                put("centerX", centerX)
                put("centerY", centerY)
                if (node.isClickable) put("clickable", true)
                if (node.isScrollable) put("scrollable", true)
            })
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findMatchingNodes(child, query, matches)
            child.recycle()
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/ScreenTreeSerializer.kt
git commit -m "feat(uiautomation): add ScreenTreeSerializer for accessibility tree to JSON"
```

---

### Task 5: GestureHelper

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/GestureHelper.kt`

- [ ] **Step 1: Create GestureHelper**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/GestureHelper.kt`:

```kotlin
package se.premex.mcp.uiautomation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object GestureHelper {

    suspend fun tap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        return dispatchGesture(service, stroke)
    }

    suspend fun longPress(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1000)
        return dispatchGesture(service, stroke)
    }

    suspend fun swipe(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGesture(service, stroke)
    }

    private suspend fun dispatchGesture(
        service: AccessibilityService,
        stroke: GestureDescription.StrokeDescription
    ): Boolean = suspendCoroutine { continuation ->
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            },
            null
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/service/GestureHelper.kt
git commit -m "feat(uiautomation): add GestureHelper for tap, long press, and swipe gestures"
```

---

### Task 6: UiAutomationToolConfigurator Interface

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfigurator.kt`

- [ ] **Step 1: Create the interface**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfigurator.kt`:

```kotlin
package se.premex.mcp.uiautomation.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface UiAutomationToolConfigurator {
    fun configure(server: Server)
}
```

- [ ] **Step 2: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfigurator.kt
git commit -m "feat(uiautomation): add UiAutomationToolConfigurator interface"
```

---

### Task 7: UiAutomationToolConfiguratorImpl — Screen Reading Tools

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfiguratorImpl.kt`

This task creates the configurator with `phone_get_screen`, `phone_screenshot`, `phone_get_screen_info`, and `phone_find_node`. Task 8 adds the interaction tools to the same file.

- [ ] **Step 1: Create UiAutomationToolConfiguratorImpl with screen reading + utility tools**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/UiAutomationToolConfiguratorImpl.kt`:

```kotlin
package se.premex.mcp.uiautomation.configurator

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import se.premex.mcp.uiautomation.service.AccessibilityServiceConnection
import se.premex.mcp.uiautomation.service.GestureHelper
import se.premex.mcp.uiautomation.service.ScreenTreeSerializer
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class UiAutomationToolConfiguratorImpl(
    private val context: Context
) : UiAutomationToolConfigurator {

    private fun getServiceOrError(): Pair<AccessibilityService?, CallToolResult?> {
        val service = AccessibilityServiceConnection.instance
        if (service == null) {
            return null to CallToolResult(
                content = listOf(
                    TextContent(
                        "Accessibility Service is not enabled. Please enable 'Phone MCP' in Settings > Accessibility."
                    )
                )
            )
        }
        return service to null
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(message))
        )
    }

    private fun successResult(message: String): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(message))
        )
    }

    override fun configure(server: Server) {
        configureScreenReading(server)
        configureInteraction(server)
        configureUtility(server)
    }

    private fun configureScreenReading(server: Server) {
        server.addTool(
            name = "phone_get_screen",
            description = """
                Get the accessibility tree of the current screen as a flat list of UI nodes.
                Each node includes: class name, bounds, text, content description, resource ID,
                and interaction flags (clickable, scrollable, focused, etc.).
                Use this to understand what is on screen and find elements to interact with.
            """.trimIndent(),
        ) { _ ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val root = service!!.rootInActiveWindow
            if (root == null) {
                return@addTool errorResult("No active window available.")
            }

            val packageName = root.packageName?.toString()
            // Try to get the active window's title as activity name
            val activityName = service.windows
                ?.firstOrNull { it.isActive }
                ?.title?.toString()

            val tree = ScreenTreeSerializer.serialize(root, packageName, activityName)
            root.recycle()

            CallToolResult(
                content = listOf(TextContent(tree.toString()))
            )
        }

        server.addTool(
            name = "phone_screenshot",
            description = """
                Take a screenshot of the current screen. Returns a base64-encoded PNG image
                scaled to max 720px wide. Requires Android 11 (API 30) or higher.
            """.trimIndent(),
        ) { _ ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@addTool errorResult(
                    "Screenshots require Android 11 (API 30) or higher. " +
                        "This device is running API ${Build.VERSION.SDK_INT}. " +
                        "Use phone_get_screen for a text representation of the screen instead."
                )
            }

            val bitmap = runBlocking { takeScreenshot(service!!) }
                ?: return@addTool errorResult("Failed to capture screenshot.")

            // Scale down to max 720px wide
            val scaled = if (bitmap.width > 720) {
                val ratio = 720f / bitmap.width
                val newHeight = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, 720, newHeight, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
            scaled.recycle()
            val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)

            CallToolResult(
                content = listOf(
                    ImageContent(data = base64, mimeType = "image/png"),
                    TextContent("Screenshot captured successfully.")
                )
            )
        }
    }

    private suspend fun takeScreenshot(service: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return suspendCoroutine { continuation ->
            service.takeScreenshot(
                0,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback() {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hwBitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()
                        // Convert hardware bitmap to software bitmap for compression
                        val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBitmap?.recycle()
                        continuation.resume(swBitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    private fun configureInteraction(server: Server) {
        server.addTool(
            name = "phone_tap",
            description = "Tap at the specified screen coordinates.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("x") {
                        put("type", "number")
                        put("description", "X coordinate to tap")
                    }
                    putJsonObject("y") {
                        put("type", "number")
                        put("description", "Y coordinate to tap")
                    }
                },
                required = listOf("x", "y")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val x = request.arguments?.get("x")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'x' parameter.")
            val y = request.arguments?.get("y")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'y' parameter.")

            val success = runBlocking { GestureHelper.tap(service!!, x, y) }
            if (success) successResult("Tapped at ($x, $y)")
            else errorResult("Tap gesture was cancelled.")
        }

        server.addTool(
            name = "phone_long_press",
            description = "Long press at the specified screen coordinates.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("x") {
                        put("type", "number")
                        put("description", "X coordinate to long press")
                    }
                    putJsonObject("y") {
                        put("type", "number")
                        put("description", "Y coordinate to long press")
                    }
                },
                required = listOf("x", "y")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val x = request.arguments?.get("x")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'x' parameter.")
            val y = request.arguments?.get("y")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'y' parameter.")

            val success = runBlocking { GestureHelper.longPress(service!!, x, y) }
            if (success) successResult("Long pressed at ($x, $y)")
            else errorResult("Long press gesture was cancelled.")
        }

        server.addTool(
            name = "phone_swipe",
            description = "Swipe from one point to another on the screen.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("startX") {
                        put("type", "number")
                        put("description", "Start X coordinate")
                    }
                    putJsonObject("startY") {
                        put("type", "number")
                        put("description", "Start Y coordinate")
                    }
                    putJsonObject("endX") {
                        put("type", "number")
                        put("description", "End X coordinate")
                    }
                    putJsonObject("endY") {
                        put("type", "number")
                        put("description", "End Y coordinate")
                    }
                    putJsonObject("durationMs") {
                        put("type", "integer")
                        put("description", "Swipe duration in milliseconds (default: 300)")
                    }
                },
                required = listOf("startX", "startY", "endX", "endY")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val startX = request.arguments?.get("startX")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'startX' parameter.")
            val startY = request.arguments?.get("startY")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'startY' parameter.")
            val endX = request.arguments?.get("endX")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'endX' parameter.")
            val endY = request.arguments?.get("endY")?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@addTool errorResult("Missing or invalid 'endY' parameter.")
            val durationMs = request.arguments?.get("durationMs")?.jsonPrimitive?.content?.toLongOrNull() ?: 300L

            val success = runBlocking { GestureHelper.swipe(service!!, startX, startY, endX, endY, durationMs) }
            if (success) successResult("Swiped from ($startX, $startY) to ($endX, $endY)")
            else errorResult("Swipe gesture was cancelled.")
        }

        server.addTool(
            name = "phone_type_text",
            description = "Type text into the currently focused input field.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "The text to type")
                    }
                },
                required = listOf("text")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val text = request.arguments?.get("text")?.jsonPrimitive?.content
                ?: return@addTool errorResult("Missing 'text' parameter.")

            val focused = service!!.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) {
                return@addTool errorResult("No input field is currently focused. Tap an input field first.")
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()

            if (success) successResult("Typed text into focused field.")
            else errorResult("Failed to set text. The field may not support direct text input.")
        }

        server.addTool(
            name = "phone_press_key",
            description = """
                Press a system key. Supported keys: back, home, recents, notifications, enter, delete.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("key") {
                        put("type", "string")
                        put("description", "Key to press: back, home, recents, notifications, enter, delete")
                    }
                },
                required = listOf("key")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val key = request.arguments?.get("key")?.jsonPrimitive?.content?.lowercase()
                ?: return@addTool errorResult("Missing 'key' parameter.")

            val globalAction = when (key) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                else -> null
            }

            if (globalAction != null) {
                val success = service!!.performGlobalAction(globalAction)
                return@addTool if (success) successResult("Pressed $key key.")
                else errorResult("Failed to press $key key.")
            }

            // Handle enter/delete via focused node actions
            when (key) {
                "enter" -> {
                    val focused = service!!.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focused != null) {
                        // Try pressing enter via IME action
                        val args = Bundle().apply {
                            putInt(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                            )
                        }
                        val success = focused.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args)
                        focused.recycle()
                        // Fallback: append newline
                        if (!success) {
                            val currentFocused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            if (currentFocused != null) {
                                val currentText = currentFocused.text?.toString() ?: ""
                                val textArgs = Bundle().apply {
                                    putCharSequence(
                                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                        currentText + "\n"
                                    )
                                }
                                currentFocused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArgs)
                                currentFocused.recycle()
                            }
                        }
                        return@addTool successResult("Pressed enter.")
                    }
                    return@addTool errorResult("No focused input field for enter key.")
                }
                "delete" -> {
                    val focused = service!!.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focused != null) {
                        val currentText = focused.text?.toString() ?: ""
                        if (currentText.isNotEmpty()) {
                            val newText = currentText.dropLast(1)
                            val args = Bundle().apply {
                                putCharSequence(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    newText
                                )
                            }
                            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                        focused.recycle()
                        return@addTool successResult("Pressed delete.")
                    }
                    return@addTool errorResult("No focused input field for delete key.")
                }
                else -> return@addTool errorResult("Unknown key '$key'. Supported: back, home, recents, notifications, enter, delete.")
            }
        }

        server.addTool(
            name = "phone_scroll",
            description = """
                Scroll a scrollable element on screen. If no index is provided, scrolls the
                first scrollable element found. Directions: up, down, left, right.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("direction") {
                        put("type", "string")
                        put("description", "Scroll direction: up, down, left, right")
                    }
                    putJsonObject("index") {
                        put("type", "integer")
                        put("description", "Optional node index from phone_get_screen output to scroll a specific element")
                    }
                },
                required = listOf("direction")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val direction = request.arguments?.get("direction")?.jsonPrimitive?.content?.lowercase()
                ?: return@addTool errorResult("Missing 'direction' parameter.")
            val nodeIndex = request.arguments?.get("index")?.jsonPrimitive?.content?.toIntOrNull()

            val root = service!!.rootInActiveWindow
                ?: return@addTool errorResult("No active window available.")

            val scrollableNode = if (nodeIndex != null) {
                findNodeByIndex(root, nodeIndex)
            } else {
                findFirstScrollable(root)
            }

            if (scrollableNode == null) {
                root.recycle()
                return@addTool errorResult("No scrollable element found.")
            }

            val action = when (direction) {
                "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                "right" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
                    } else {
                        // Fallback: use swipe gesture
                        val bounds = android.graphics.Rect()
                        scrollableNode.getBoundsInScreen(bounds)
                        scrollableNode.recycle()
                        root.recycle()
                        val success = runBlocking {
                            GestureHelper.swipe(
                                service, bounds.centerX().toFloat(), bounds.centerY().toFloat(),
                                bounds.left.toFloat(), bounds.centerY().toFloat(), 300
                            )
                        }
                        return@addTool if (success) successResult("Scrolled right.")
                        else errorResult("Scroll right gesture was cancelled.")
                    }
                }
                "left" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
                    } else {
                        val bounds = android.graphics.Rect()
                        scrollableNode.getBoundsInScreen(bounds)
                        scrollableNode.recycle()
                        root.recycle()
                        val success = runBlocking {
                            GestureHelper.swipe(
                                service, bounds.left.toFloat(), bounds.centerY().toFloat(),
                                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 300
                            )
                        }
                        return@addTool if (success) successResult("Scrolled left.")
                        else errorResult("Scroll left gesture was cancelled.")
                    }
                }
                else -> {
                    scrollableNode.recycle()
                    root.recycle()
                    return@addTool errorResult("Unknown direction '$direction'. Use: up, down, left, right.")
                }
            }

            val success = scrollableNode.performAction(action)
            scrollableNode.recycle()
            root.recycle()

            if (success) successResult("Scrolled $direction.")
            else errorResult("Scroll $direction failed.")
        }
    }

    private fun configureUtility(server: Server) {
        server.addTool(
            name = "phone_get_screen_info",
            description = "Get screen dimensions, pixel density, and rotation.",
        ) { _ ->
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            @Suppress("DEPRECATION")
            val rotation = windowManager.defaultDisplay.rotation

            val info = buildJsonObject {
                put("widthPixels", metrics.widthPixels)
                put("heightPixels", metrics.heightPixels)
                put("density", metrics.density.toDouble())
                put("densityDpi", metrics.densityDpi)
                put("rotation", rotation)
                put("rotationLabel", when (rotation) {
                    0 -> "portrait"
                    1 -> "landscape_left"
                    2 -> "portrait_upside_down"
                    3 -> "landscape_right"
                    else -> "unknown"
                })
            }

            CallToolResult(
                content = listOf(TextContent(info.toString()))
            )
        }

        server.addTool(
            name = "phone_find_node",
            description = """
                Find UI nodes matching a text query. Searches node text, content description,
                and resource ID. Returns matching nodes with their bounds and center coordinates,
                useful for finding tap targets.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Text to search for (case-insensitive, partial match)")
                    }
                },
                required = listOf("query")
            )
        ) { request ->
            val (service, error) = getServiceOrError()
            if (error != null) return@addTool error

            val query = request.arguments?.get("query")?.jsonPrimitive?.content
                ?: return@addTool errorResult("Missing 'query' parameter.")

            val root = service!!.rootInActiveWindow
                ?: return@addTool errorResult("No active window available.")

            val matches = ScreenTreeSerializer.findNodes(root, query)
            root.recycle()

            if (matches.isEmpty()) {
                return@addTool successResult("No nodes found matching '$query'.")
            }

            CallToolResult(
                content = listOf(TextContent(matches.toString()))
            )
        }
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstScrollable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findNodeByIndex(root: AccessibilityNodeInfo, targetIndex: Int): AccessibilityNodeInfo? {
        var currentIndex = 0
        return findNodeByIndexRecursive(root, targetIndex, { currentIndex++ ; currentIndex - 1 })
    }

    private fun findNodeByIndexRecursive(
        node: AccessibilityNodeInfo,
        targetIndex: Int,
        nextIndex: () -> Int
    ): AccessibilityNodeInfo? {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        if (node.packageName?.toString() == "com.android.systemui") return null

        val idx = nextIndex()
        if (idx == targetIndex) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByIndexRecursive(child, targetIndex, nextIndex)
            if (result != null) return result
            child.recycle()
        }
        return null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/configurator/
git commit -m "feat(uiautomation): implement all MCP tools in UiAutomationToolConfiguratorImpl"
```

---

### Task 8: UiAutomationTool + DI Module

**Files:**
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/tool/UiAutomationTool.kt`
- Create: `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/di/UiAutomationToolModule.kt`

- [ ] **Step 1: Create UiAutomationTool**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/tool/UiAutomationTool.kt`:

```kotlin
package se.premex.mcp.uiautomation.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.uiautomation.configurator.UiAutomationToolConfiguratorImpl

class UiAutomationTool(
    private val configurator: UiAutomationToolConfiguratorImpl
) : McpTool {
    override val id: String = "uiautomation"
    override val name: String = "UI Automation"
    override val enabledByDefault: Boolean = false
    override val disclaim: String = "This tool has full access to your screen content and can perform " +
        "actions on your behalf. It can read everything visible on screen, including sensitive " +
        "information like passwords, messages, and financial data."

    override fun configure(server: Server) {
        configurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        // Accessibility service is enabled via Settings, not runtime permissions
        return emptySet()
    }
}
```

- [ ] **Step 2: Create UiAutomationToolModule**

Create `tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/di/UiAutomationToolModule.kt`:

```kotlin
package se.premex.mcp.uiautomation.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.uiautomation.configurator.UiAutomationToolConfiguratorImpl
import se.premex.mcp.uiautomation.tool.UiAutomationTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiAutomationToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideUiAutomationTool(@ApplicationContext context: Context): McpTool {
        val configurator = UiAutomationToolConfiguratorImpl(context)
        return UiAutomationTool(configurator)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/tool/ tools/uiautomation/src/main/java/se/premex/mcp/uiautomation/di/
git commit -m "feat(uiautomation): add UiAutomationTool and Hilt DI module"
```

---

### Task 9: Build Verification

- [ ] **Step 1: Run full debug build**

Run: `./gradlew assembleFullDebug`

Expected: BUILD SUCCESSFUL. The uiautomation module compiles and the app includes it.

- [ ] **Step 2: Run lint**

Run: `./gradlew :tools:uiautomation:lint`

Expected: No errors. Warnings about API version checks for `takeScreenshot` (API 30) are expected and already handled with `Build.VERSION.SDK_INT` checks.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL. No new tests added (matching existing tool module pattern), existing tests still pass.

- [ ] **Step 4: Final commit (if any lint fixes needed)**

If lint found issues, fix them and commit:

```bash
git add -A tools/uiautomation/
git commit -m "fix(uiautomation): address lint warnings"
```
