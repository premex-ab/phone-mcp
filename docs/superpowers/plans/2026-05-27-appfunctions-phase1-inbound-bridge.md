# AppFunctions Phase 1 — Inbound Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `tools/appfunctions` module that discovers every AppFunction registered on the device (API 36+) and exposes each as an MCP tool on the existing SSE server, forwarding invocations through `AppFunctionManager.executeAppFunction(...)`.

**Architecture:** New tool module mirroring the existing `tools/externaltools` shape (configurator + repository + tool + Hilt DI). At server start, the configurator queries the platform for registered AppFunctions and registers one `server.addTool(...)` per discovered function. A small schema-mapping helper in `:core` converts AppFunctions parameter metadata to MCP `ToolSchema`. Behaviour gated at runtime by `Build.VERSION.SDK_INT >= 36`.

**Tech Stack:** Kotlin 2.3.20, JVM 21, Android `compileSdk 36`, `minSdk 24`, Hilt 2.57.2, MCP Kotlin SDK 0.8.1, `androidx.appfunctions` Jetpack library (new dep), kotlinx.serialization for JSON schema building.

**Spec:** [docs/superpowers/specs/2026-05-27-appfunctions-integration-design.md](../specs/2026-05-27-appfunctions-integration-design.md) — Phase 1 only.

**Phase 2 (outbound dual-publish)** is a separate plan, deferred until this Phase 1 plan is shipped and verified.

---

## Task 1: Add Gradle catalog entries for `androidx.appfunctions`

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Look up the current `androidx.appfunctions` version on maven.google.com**

Visit https://maven.google.com/web/index.html#androidx.appfunctions:appfunctions and note the latest stable (or alpha if no stable yet) version. Phase 1 uses the runtime artifact only (`androidx.appfunctions:appfunctions`) — we do NOT need `appfunctions-service` or `appfunctions-compiler` (those are for publishing `@AppFunction`s, which is Phase 2).

- [ ] **Step 2: Add the version reference under `[versions]`**

Edit `gradle/libs.versions.toml`. After `cameraxVersion = "1.5.2"` (line 21), add:

```toml
androidxAppfunctions = "<VERSION_FROM_STEP_1>"
```

- [ ] **Step 3: Add the library entry under `[libraries]`**

After `androidx-camera-extensions = ...` (line 76), add:

```toml
androidx-appfunctions = { group = "androidx.appfunctions", name = "appfunctions", version.ref = "androidxAppfunctions" }
```

- [ ] **Step 4: Verify the catalog file parses**

Run: `./gradlew help -q`
Expected: build succeeds, no "Could not resolve" or version-catalog parsing errors.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add androidx.appfunctions to version catalog"
```

---

## Task 2: Add JUnit test dependency to `:core`

**Files:**
- Modify: `core/build.gradle.kts`

The `:core` module is a pure-JVM library (`mcp.jvm.library` convention) with no tests today. Adding the AppFunctions schema mapper means we need unit tests there.

- [ ] **Step 1: Add the test dependency**

Replace the contents of `core/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.mcp.jvm.library)
}

dependencies {
    implementation(libs.io.modelcontextprotocol.kotlin.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
```

- [ ] **Step 2: Verify the module still builds**

Run: `./gradlew :core:compileTestKotlin`
Expected: BUILD SUCCESSFUL with no compile errors. No tests exist yet, so nothing executes.

- [ ] **Step 3: Commit**

```bash
git add core/build.gradle.kts
git commit -m "build(core): add JUnit test dependency"
```

---

## Task 3: AppFunctionSchemaMapper — TDD

**Files:**
- Create: `core/src/main/java/se/premex/mcp/core/tool/AppFunctionParameterSpec.kt`
- Create: `core/src/main/java/se/premex/mcp/core/tool/AppFunctionSchemaMapper.kt`
- Create: `core/src/test/java/se/premex/mcp/core/tool/AppFunctionSchemaMapperTest.kt`

The mapper takes a list of parameter specs (our own JVM-only data class) and produces an MCP `ToolSchema`. It has zero Android dependencies, lives in `:core`, and is the only piece of logic in this plan that's easy to unit-test in pure JVM. The configurator (Task 7) will extract `AppFunctionParameterSpec` instances from the platform's `androidx.appfunctions` metadata and call this mapper.

- [ ] **Step 1: Create the parameter-spec data class**

Create `core/src/main/java/se/premex/mcp/core/tool/AppFunctionParameterSpec.kt`:

```kotlin
package se.premex.mcp.core.tool

/**
 * Generic, Android-free description of one parameter on a discovered AppFunction.
 *
 * Produced by the tools/appfunctions configurator from the platform's
 * androidx.appfunctions parameter metadata, then handed to AppFunctionSchemaMapper
 * to project into an MCP ToolSchema.
 */
data class AppFunctionParameterSpec(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean,
) {
    /**
     * Subset of AppFunctions parameter types supported by the Phase 1 bridge.
     * Functions with parameters outside this set are skipped during discovery
     * (logged at WARN by the configurator).
     */
    enum class ParameterType { STRING, INTEGER, LONG, BOOLEAN, NUMBER, STRING_ARRAY }
}
```

- [ ] **Step 2: Write the failing tests**

Create `core/src/test/java/se/premex/mcp/core/tool/AppFunctionSchemaMapperTest.kt`:

```kotlin
package se.premex.mcp.core.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFunctionSchemaMapperTest {

    @Test
    fun `maps a single required string parameter`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec(
                    name = "recipient",
                    type = AppFunctionParameterSpec.ParameterType.STRING,
                    description = "Phone number to send to.",
                    required = true,
                )
            )
        )

        val props = schema.properties
        assertTrue(props.containsKey("recipient"))
        val recipient = props["recipient"] as JsonObject
        assertEquals(JsonPrimitive("string"), recipient["type"])
        assertEquals(JsonPrimitive("Phone number to send to."), recipient["description"])
        assertEquals(listOf("recipient"), schema.required)
    }

    @Test
    fun `omits optional parameters from required list but keeps them in properties`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("a", AppFunctionParameterSpec.ParameterType.STRING, "first", required = true),
                AppFunctionParameterSpec("b", AppFunctionParameterSpec.ParameterType.STRING, "second", required = false),
            )
        )

        assertEquals(listOf("a"), schema.required)
        assertTrue(schema.properties.containsKey("a"))
        assertTrue(schema.properties.containsKey("b"))
    }

    @Test
    fun `maps each supported primitive type to the correct JSON schema type`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("s", AppFunctionParameterSpec.ParameterType.STRING, "", required = true),
                AppFunctionParameterSpec("i", AppFunctionParameterSpec.ParameterType.INTEGER, "", required = true),
                AppFunctionParameterSpec("l", AppFunctionParameterSpec.ParameterType.LONG, "", required = true),
                AppFunctionParameterSpec("b", AppFunctionParameterSpec.ParameterType.BOOLEAN, "", required = true),
                AppFunctionParameterSpec("n", AppFunctionParameterSpec.ParameterType.NUMBER, "", required = true),
            )
        )

        assertEquals(JsonPrimitive("string"),  (schema.properties["s"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("integer"), (schema.properties["i"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("integer"), (schema.properties["l"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("boolean"), (schema.properties["b"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("number"),  (schema.properties["n"] as JsonObject)["type"])
    }

    @Test
    fun `maps STRING_ARRAY to array with string items`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("tags", AppFunctionParameterSpec.ParameterType.STRING_ARRAY, "Tag list", required = false),
            )
        )

        val tags = schema.properties["tags"] as JsonObject
        assertEquals(JsonPrimitive("array"), tags["type"])
        val items = tags["items"] as JsonObject
        assertEquals(JsonPrimitive("string"), items["type"])
    }

    @Test
    fun `handles empty parameter list`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(emptyList())
        assertTrue(schema.properties.isEmpty())
        assertTrue(schema.required.isEmpty())
    }
}
```

- [ ] **Step 3: Run the tests — they must fail (mapper doesn't exist)**

Run: `./gradlew :core:test`
Expected: compile failure — `AppFunctionSchemaMapper` is unresolved.

- [ ] **Step 4: Implement the mapper**

Create `core/src/main/java/se/premex/mcp/core/tool/AppFunctionSchemaMapper.kt`:

```kotlin
package se.premex.mcp.core.tool

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Converts a list of AppFunction parameter specs into an MCP ToolSchema.
 *
 * Pure JVM, no Android dependencies. Used by tools/appfunctions to project
 * platform AppFunction metadata into the MCP tool registration format.
 */
object AppFunctionSchemaMapper {

    fun toMcpToolSchema(parameters: List<AppFunctionParameterSpec>): ToolSchema {
        val properties = buildJsonObject {
            parameters.forEach { param ->
                put(param.name, buildJsonObject {
                    when (param.type) {
                        AppFunctionParameterSpec.ParameterType.STRING ->
                            put("type", JsonPrimitive("string"))
                        AppFunctionParameterSpec.ParameterType.INTEGER,
                        AppFunctionParameterSpec.ParameterType.LONG ->
                            put("type", JsonPrimitive("integer"))
                        AppFunctionParameterSpec.ParameterType.BOOLEAN ->
                            put("type", JsonPrimitive("boolean"))
                        AppFunctionParameterSpec.ParameterType.NUMBER ->
                            put("type", JsonPrimitive("number"))
                        AppFunctionParameterSpec.ParameterType.STRING_ARRAY -> {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        }
                    }
                    if (param.description.isNotEmpty()) {
                        put("description", JsonPrimitive(param.description))
                    }
                })
            }
        }

        val required = parameters.filter { it.required }.map { it.name }

        return ToolSchema(properties = properties, required = required)
    }
}
```

- [ ] **Step 5: Run the tests — they must pass**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL. 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/se/premex/mcp/core/tool/AppFunctionParameterSpec.kt \
        core/src/main/java/se/premex/mcp/core/tool/AppFunctionSchemaMapper.kt \
        core/src/test/java/se/premex/mcp/core/tool/AppFunctionSchemaMapperTest.kt
git commit -m "feat(core): add AppFunctionSchemaMapper for MCP tool schema projection"
```

---

## Task 4: Scaffold the `tools/appfunctions` module

**Files:**
- Create: `tools/appfunctions/build.gradle.kts`
- Create: `tools/appfunctions/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

This task creates the empty module shell, registers it with Gradle, and adds it as an app dependency. No Kotlin sources yet — those come in Tasks 5–8.

- [ ] **Step 1: Create the module's `build.gradle.kts`**

Create `tools/appfunctions/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.appfunctions"
}

dependencies {
    implementation(libs.androidx.appfunctions)
}
```

The `mcp.android.tool` convention plugin already provides: `:core`, MCP SDK, Ktor client, Hilt, AndroidX core/appcompat/material, JUnit, Espresso, `compileSdk 36` / `minSdk 24`. We only add the AppFunctions Jetpack lib explicitly.

- [ ] **Step 2: Create the AndroidManifest**

Create `tools/appfunctions/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!--
        AppFunctions discovery uses AppFunctionManager (API 36+).
        EXECUTE_APP_FUNCTIONS may not be granted to third-party apps for all
        target functions — at runtime we surface per-function permission errors
        in the tool's CallToolResult. No build-time permission declaration is
        required by the platform, but we list it here for forward-compat with
        future Android releases.
    -->
    <uses-permission android:name="android.permission.EXECUTE_APP_FUNCTIONS" />
</manifest>
```

- [ ] **Step 3: Register the module with Gradle**

Edit `settings.gradle.kts`. After `include(":tools:camera")` (line 43) add:

```kotlin
include(":tools:appfunctions")
```

- [ ] **Step 4: Add the module as an app dependency**

Edit `app/build.gradle.kts`. In the `dependencies` block, after `implementation(project(":tools:camera"))` (line 126), add:

```kotlin
    implementation(project(":tools:appfunctions"))
```

- [ ] **Step 5: Verify the module is discoverable by Gradle**

Run: `./gradlew :tools:appfunctions:tasks -q | head -20`
Expected: lists Android build tasks (`assembleDebug`, `assembleRelease`, etc.) — confirms the module is configured.

- [ ] **Step 6: Commit**

```bash
git add tools/appfunctions/build.gradle.kts \
        tools/appfunctions/src/main/AndroidManifest.xml \
        settings.gradle.kts \
        app/build.gradle.kts
git commit -m "build: scaffold tools/appfunctions module"
```

---

## Task 5: Data class `AppFunctionMetadataInfo`

**Files:**
- Create: `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/repositories/AppFunctionMetadataInfo.kt`

Internal projection of one discovered AppFunction. Stable, test-friendly representation built once per discovery; not persisted.

- [ ] **Step 1: Create the data class**

Create `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/repositories/AppFunctionMetadataInfo.kt`:

```kotlin
package se.premex.mcp.appfunctions.repositories

import se.premex.mcp.core.tool.AppFunctionParameterSpec

/**
 * One AppFunction discovered on the device, projected into a stable internal shape.
 *
 * Built by AppFunctionsConfiguratorImpl from the platform's AppFunctionMetadata
 * returned by AppFunctionManager. The mcpToolName is the sanitized name used
 * when registering the function as an MCP tool on the SSE server.
 */
data class AppFunctionMetadataInfo(
    val packageName: String,
    val functionId: String,
    val mcpToolName: String,
    val description: String,
    val parameters: List<AppFunctionParameterSpec>,
) {
    companion object {
        /**
         * Builds the MCP tool name used to expose this function over SSE.
         * Sanitizes the package + function identifier into a single MCP-safe token.
         */
        fun mcpToolNameFor(packageName: String, functionId: String): String {
            val sanitized = "appfn__${packageName}__${functionId}"
                .replace(Regex("[^A-Za-z0-9_]"), "_")
            return sanitized
        }
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :tools:appfunctions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/repositories/AppFunctionMetadataInfo.kt
git commit -m "feat(appfunctions): add AppFunctionMetadataInfo data class"
```

---

## Task 6: `AppFunctionsConfigurator` interface

**Files:**
- Create: `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/configurator/AppFunctionsConfigurator.kt`

- [ ] **Step 1: Create the interface**

Create `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/configurator/AppFunctionsConfigurator.kt`:

```kotlin
package se.premex.mcp.appfunctions.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Discovers AppFunctions registered on the device and configures each as an
 * MCP tool on the given server.
 *
 * On Android < 16 (API < 36) or when the AppFunctions service is unavailable,
 * implementations log and register no tools rather than throw.
 */
interface AppFunctionsConfigurator {
    fun configureTools(server: Server)
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :tools:appfunctions:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/configurator/AppFunctionsConfigurator.kt
git commit -m "feat(appfunctions): add AppFunctionsConfigurator interface"
```

---

## Task 7: `AppFunctionsConfiguratorImpl` — discovery + invocation

**Files:**
- Create: `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/configurator/AppFunctionsConfiguratorImpl.kt`

This is the heart of the bridge. The `androidx.appfunctions` Jetpack API surface is new — confirm method names against the IDE/source-jar before assuming the code below compiles unchanged.

- [ ] **Step 1: Confirm the `androidx.appfunctions` API surface**

Sync Gradle so the lib resolves, then in Android Studio (or by inspecting `~/.gradle/caches/modules-2/files-2.1/androidx.appfunctions/appfunctions/`) confirm the following symbols exist; adjust the code below if the lib has renamed any of them:

- `androidx.appfunctions.AppFunctionManager` — entry-point service obtained via `context.getSystemService(AppFunctionManager::class.java)` OR a static factory like `AppFunctionManager.getInstance(context)`.
- A suspending or callback-based way to enumerate registered AppFunctions for the current user (likely `searchAppFunctions(spec)` or `observeAppFunctions(spec)` returning a `Flow`). Take the first emission if it's a Flow.
- A suspending `executeAppFunction(request)` taking an `ExecuteAppFunctionRequest` (or similarly named) and returning an `ExecuteAppFunctionResponse`.
- A metadata type (likely `AppFunctionMetadata` or `AppFunctionRuntimeMetadata`) carrying `packageName`, function `id`, `description`, and a `parameters` schema.

If the actual names differ, update the code in Step 2 accordingly. The structure (manager → query → for-each register → on call execute) is invariant.

- [ ] **Step 2: Implement the configurator**

Create `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/configurator/AppFunctionsConfiguratorImpl.kt`:

```kotlin
package se.premex.mcp.appfunctions.configurator

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.metadata.AppFunctionMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import se.premex.mcp.appfunctions.repositories.AppFunctionMetadataInfo
import se.premex.mcp.core.tool.AppFunctionParameterSpec
import se.premex.mcp.core.tool.AppFunctionSchemaMapper
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppFunctionsConfig"

@Singleton
class AppFunctionsConfiguratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppFunctionsConfigurator {

    override fun configureTools(server: Server) {
        if (Build.VERSION.SDK_INT < 36) {
            Log.i(TAG, "AppFunctions unavailable on SDK < 36 (current=${Build.VERSION.SDK_INT}), skipping")
            return
        }

        val manager = try {
            context.getSystemService(AppFunctionManager::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain AppFunctionManager", e)
            null
        }
        if (manager == null) {
            Log.w(TAG, "AppFunctionManager unavailable on this device, skipping")
            return
        }

        val discovered = discoverAppFunctions(manager)
        Log.i(TAG, "Discovered ${discovered.size} AppFunctions across ${discovered.map { it.packageName }.toSet().size} packages")

        discovered.forEach { info ->
            try {
                val schema = AppFunctionSchemaMapper.toMcpToolSchema(info.parameters)
                server.addTool(
                    name = info.mcpToolName,
                    description = info.description,
                    inputSchema = schema,
                ) { request ->
                    invokeAppFunction(manager, info, request.arguments ?: emptyMap())
                }
                Log.d(TAG, "Registered ${info.mcpToolName} from ${info.packageName}")
            } catch (e: Exception) {
                Log.w(TAG, "Skipping ${info.packageName}/${info.functionId}: ${e.message}")
            }
        }
    }

    private fun discoverAppFunctions(manager: AppFunctionManager): List<AppFunctionMetadataInfo> {
        return try {
            // Adjust the search-spec / call shape to match the actual androidx.appfunctions API
            // confirmed in Task 7 Step 1. If the API is Flow-based, take the first emission via
            // runBlocking { manager.observeAppFunctions(AppFunctionSearchSpec()).first() } — Server
            // start runs on a background thread, so blocking briefly here is acceptable.
            kotlinx.coroutines.runBlocking {
                manager.observeAppFunctions(AppFunctionSearchSpec()).first()
                    .mapNotNull { platformMetadata ->
                        runCatching { toMetadataInfo(platformMetadata) }
                            .onFailure { Log.w(TAG, "Skipping function with unsupported schema: ${it.message}") }
                            .getOrNull()
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AppFunction discovery failed", e)
            emptyList()
        }
    }

    private fun toMetadataInfo(meta: AppFunctionMetadata): AppFunctionMetadataInfo {
        val packageName = meta.packageName
        val functionId = meta.id
        val description = meta.description.orEmpty().ifEmpty { "AppFunction $functionId from $packageName" }

        val params = meta.parameters.map { p ->
            AppFunctionParameterSpec(
                name = p.name,
                type = mapPlatformType(p.dataType),
                description = p.description.orEmpty(),
                required = p.isRequired,
            )
        }

        return AppFunctionMetadataInfo(
            packageName = packageName,
            functionId = functionId,
            mcpToolName = AppFunctionMetadataInfo.mcpToolNameFor(packageName, functionId),
            description = description,
            parameters = params,
        )
    }

    private fun mapPlatformType(platformType: Any): AppFunctionParameterSpec.ParameterType {
        // Adjust the right-hand-side identifiers to whatever the lib's parameter-type enum/sealed
        // class names actually are once confirmed in Step 1. Anything not listed throws and the
        // caller logs + skips the whole function.
        return when (platformType.toString().lowercase()) {
            "string"  -> AppFunctionParameterSpec.ParameterType.STRING
            "int", "integer" -> AppFunctionParameterSpec.ParameterType.INTEGER
            "long"    -> AppFunctionParameterSpec.ParameterType.LONG
            "boolean" -> AppFunctionParameterSpec.ParameterType.BOOLEAN
            "float", "double", "number" -> AppFunctionParameterSpec.ParameterType.NUMBER
            "stringarray", "list<string>" -> AppFunctionParameterSpec.ParameterType.STRING_ARRAY
            else -> throw IllegalArgumentException("Unsupported AppFunction parameter type: $platformType")
        }
    }

    private fun invokeAppFunction(
        manager: AppFunctionManager,
        info: AppFunctionMetadataInfo,
        arguments: Map<String, JsonElement>,
    ): CallToolResult {
        return try {
            val response = kotlinx.coroutines.runBlocking {
                val request = ExecuteAppFunctionRequest(
                    targetPackageName = info.packageName,
                    functionIdentifier = info.functionId,
                    functionParameters = argumentsToGenericDocument(arguments),
                )
                manager.executeAppFunction(request)
            }
            CallToolResult(content = listOf(TextContent(response.toString())))
        } catch (e: AppFunctionException) {
            Log.w(TAG, "AppFunction ${info.packageName}/${info.functionId} threw", e)
            CallToolResult(content = listOf(TextContent("AppFunction error: ${e.message}")))
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error invoking ${info.packageName}/${info.functionId}", e)
            CallToolResult(content = listOf(TextContent("Function no longer available; restart the MCP server to refresh the tool list.")))
        }
    }

    /**
     * Convert MCP JSON arguments into the parameter container the AppFunctions API expects.
     * The exact type (GenericDocument, Bundle, AppFunctionData…) is library-defined — adjust
     * to whatever ExecuteAppFunctionRequest's parameter field actually takes. Until confirmed,
     * pass a JsonObject and let the platform's serializer interpret it.
     */
    private fun argumentsToGenericDocument(arguments: Map<String, JsonElement>): JsonObject {
        return buildJsonObject {
            arguments.forEach { (k, v) ->
                put(k, when (v) {
                    is JsonPrimitive -> v
                    else -> v
                })
            }
        }
    }
}
```

- [ ] **Step 3: Compile and resolve API mismatches**

Run: `./gradlew :tools:appfunctions:compileDebugKotlin`

Expected: either BUILD SUCCESSFUL, or a small number of "unresolved reference" errors pointing at the exact androidx.appfunctions identifiers that differ from this code (`AppFunctionManager`, `AppFunctionSearchSpec`, `ExecuteAppFunctionRequest`, `AppFunctionMetadata`, parameter type enum). For each unresolved symbol, open the IDE's auto-complete or the library source jar to find the actual name and substitute it in. The control flow doesn't change — only identifiers.

- [ ] **Step 4: Commit**

```bash
git add tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/configurator/AppFunctionsConfiguratorImpl.kt
git commit -m "feat(appfunctions): implement AppFunctionsConfiguratorImpl with SDK gating"
```

---

## Task 8: `AppFunctionsTool` and Hilt DI module

**Files:**
- Create: `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/tool/AppFunctionsTool.kt`
- Create: `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/di/AppFunctionsModule.kt`

These two files plug the configurator into the existing `McpTool` set via Hilt multibinding, exactly like `tools/externaltools` does.

- [ ] **Step 1: Create `AppFunctionsTool`**

Create `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/tool/AppFunctionsTool.kt`:

```kotlin
package se.premex.mcp.appfunctions.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.appfunctions.configurator.AppFunctionsConfigurator
import se.premex.mcp.core.tool.McpTool
import javax.inject.Inject

/**
 * Bridges every AppFunction registered on the device (Android 16+) into the MCP
 * server as individual tools. Off by default; turning this on exposes a broad
 * surface to the connected MCP client over the bearer-token-authenticated SSE
 * channel.
 */
class AppFunctionsTool @Inject constructor(
    private val configurator: AppFunctionsConfigurator,
) : McpTool {
    override val id: String = "app_functions"
    override val name: String = "AppFunctions"
    override val enabledByDefault: Boolean = false
    override val disclaim: String? =
        "Exposes every AppFunction registered on this device (Android 16+) to the " +
            "connected MCP client. The set of exposed functions depends on which apps " +
            "are installed and what they publish. Bearer-token authentication on the " +
            "SSE server is the only gate."

    override fun configure(server: Server) {
        configurator.configureTools(server)
    }

    override fun requiredPermissions(): Set<String> = emptySet()
}
```

- [ ] **Step 2: Create the Hilt module**

Create `tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/di/AppFunctionsModule.kt`:

```kotlin
package se.premex.mcp.appfunctions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.appfunctions.configurator.AppFunctionsConfigurator
import se.premex.mcp.appfunctions.configurator.AppFunctionsConfiguratorImpl
import se.premex.mcp.appfunctions.tool.AppFunctionsTool
import se.premex.mcp.core.tool.McpTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppFunctionsModule {

    @Binds
    @Singleton
    abstract fun bindAppFunctionsConfigurator(
        impl: AppFunctionsConfiguratorImpl,
    ): AppFunctionsConfigurator

    @Binds
    @IntoSet
    abstract fun bindAppFunctionsTool(
        tool: AppFunctionsTool,
    ): McpTool
}
```

- [ ] **Step 3: Verify the module compiles end-to-end (KSP runs)**

Run: `./gradlew :tools:appfunctions:assembleDebug`
Expected: BUILD SUCCESSFUL. Hilt KSP processes the `@Module` and generates the binding code; no `@Provides`/`@Binds` errors.

- [ ] **Step 4: Commit**

```bash
git add tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/tool/AppFunctionsTool.kt \
        tools/appfunctions/src/main/java/se/premex/mcp/appfunctions/di/AppFunctionsModule.kt
git commit -m "feat(appfunctions): add AppFunctionsTool and Hilt DI bindings"
```

---

## Task 9: Full-project build verification

**Files:** none (verification only)

- [ ] **Step 1: Build the whole project in fullDebug**

Run: `./gradlew :app:assembleFullDebug`
Expected: BUILD SUCCESSFUL. App compiles with the new `tools/appfunctions` module wired in via Hilt multibinding to the existing `McpTool` set.

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. `AppFunctionSchemaMapperTest` (5 tests) passes alongside any existing unit tests.

- [ ] **Step 3: Run lint on the new module**

Run: `./gradlew :tools:appfunctions:lint`
Expected: BUILD SUCCESSFUL, no errors. Warnings are acceptable — review any that mention API-level usage of AppFunctions and confirm they're correctly gated by `Build.VERSION.SDK_INT >= 36`.

- [ ] **Step 4: Commit (if any lint baselines or generated files changed)**

```bash
git status
# If clean, skip. Otherwise:
git add -p
git commit -m "build(appfunctions): regenerate lint baseline"
```

---

## Task 10: Manual verification on an API 36 device or emulator

**Files:** none (manual verification only; results documented for the release checklist)

The CI environment may not have an API 36 emulator with the AppFunctions service available. This task validates the bridge end-to-end on real hardware once before the PR ships.

- [ ] **Step 1: Install the debug build on an API 36+ device or emulator**

Run: `./gradlew :app:installFullDebug`
Expected: app installs.

- [ ] **Step 2: Confirm the AppFunctions service is present on the device**

Run: `adb shell cmd app_function help`
Expected: prints the help text for the `app_function` shell command. If it returns `cmd: Can't find service: app_function`, this device cannot validate Phase 1; switch to an API 36+ emulator with Google services and retry.

- [ ] **Step 3: Confirm at least one AppFunction is registered on the device (sanity check)**

Run: `adb shell cmd app_function list-app-functions`
Expected: JSON list with at least one entry. If empty, install an AppFunctions-enabled test app (any sample from the AndroidX AppFunctions samples) before continuing.

- [ ] **Step 4: Launch the MCP server in the app**

In the app UI: enable the "AppFunctions" tool (off by default), then start the MCP server. Note the bearer token shown in the UI and the device's IP.

- [ ] **Step 5: Verify the bridge registered the AppFunctions as MCP tools**

```bash
TOKEN=<token-from-app-ui>
DEVICE_IP=<device-ip-from-app-ui>
curl -s -H "Authorization: Bearer $TOKEN" \
     http://$DEVICE_IP:3001/sse \
     | head -200
```

Expected: SSE stream begins; one of the early messages from the MCP server should be a `tools/list` response (or the tool list is queryable after handshake) containing tools with names starting `appfn__` — one per discovered AppFunction.

- [ ] **Step 6: Document the result in the PR description**

Capture in the PR description:
- Device model + API level used
- Output of `adb shell cmd app_function list-app-functions | jq 'length'` (count of discoverable functions)
- Whether all discovered functions appeared as MCP tools (or skipped count with reasons from `adb logcat -s AppFunctionsConfig`)

No commit for this task — manual verification only.

---

## Self-Review Summary

**Spec coverage check (against [spec sections](../specs/2026-05-27-appfunctions-integration-design.md#module-structure)):**
- ✅ `tools/appfunctions` module: Tasks 4–8
- ✅ `AppFunctionSchemaMapper` in `:core`: Tasks 2–3
- ✅ SDK gating at runtime: Task 7 Step 2 (the `Build.VERSION.SDK_INT < 36` early-return)
- ✅ Error handling row 1 (SDK gate), row 2 (null service), row 3 (discovery throws), row 4 (per-function mapping fails), row 5 (uninstalled app), row 6 (`AppFunctionException` during invocation): all in Task 7 Step 2
- ✅ Discovery cadence (snapshot at server start): Task 7 Step 2 — discovery runs once inside `configureTools(server)`
- ✅ `enabledByDefault = false` + disclaim: Task 8 Step 1
- ✅ Hilt multibinding into the existing `McpTool` set: Task 8 Step 2
- ✅ Unit testing of schema mapper: Task 3
- ✅ Manual verification on real device: Task 10
- ⚠️ **Phase 2 (outbound dual-publish):** explicitly deferred to a follow-up plan, per the spec's "Phasing" section.
- ⚠️ **Instrumented test on API 36 emulator:** the spec proposed an instrumented test calling `McpServerService` + `/sse` in-process. Replaced with the Task 10 manual ADB verification because the project's CI doesn't pin an API 36 emulator. The instrumented test can be added later once CI has an API 36 image; the test scaffolding from `app/src/androidTest/java/se/premex/mcp/externaltools/` is the template.

**Placeholder scan:** no "TBD" / "TODO" / "fill in details" remain. Task 7 Step 1 explicitly tells the engineer to verify and substitute API identifiers — this is concrete guidance, not a placeholder. Task 10 Step 6 captures concrete output, not a sketch.

**Type consistency check:** `AppFunctionParameterSpec`, `AppFunctionMetadataInfo`, `AppFunctionsConfigurator`, `AppFunctionsConfiguratorImpl`, `AppFunctionsTool`, `AppFunctionsModule` — referenced consistently across all tasks. `mcpToolNameFor` (companion) is referenced from Task 7 (`AppFunctionMetadataInfo.mcpToolNameFor(...)`) and defined in Task 5. `AppFunctionSchemaMapper.toMcpToolSchema(...)` signature matches Task 3 implementation and Task 7 usage.
