# Android AppFunctions Integration — Design Spec

> **Implementation status:** Phase 1 and Phase 2 are implemented on the AppFunctions branch.
> The Phase 2 implementation and flavor-specific decisions are recorded in
> [`2026-09-03-appfunctions-phase2-outbound-publishing.md`](../plans/2026-09-03-appfunctions-phase2-outbound-publishing.md).

## Overview

Bridge Google's [Android AppFunctions](https://developer.android.com/reference/androidx/appfunctions/package-summary) (API 36+) into this MCP server in **both** directions:

- **Phase 1 — Inbound bridge.** A new `tools/appfunctions/` module discovers every `AppFunction` registered on the device by other installed apps (Maps, Calendar, Notes, etc.) and exposes each as an MCP tool on the SSE server. A remote MCP client gains gateway access to every AppFunctions-enabled app on the phone.
- **Phase 2 — Outbound dual-publish.** Each suitable first-party tool module (`tools/sms`, `tools/smsintent`, `tools/contacts`, `tools/camera`, `tools/location`, `tools/sensor`, and `tools/files`) gains a Kotlin class with `@AppFunction`-annotated `suspend` methods. The app declares itself as an AppFunctions provider so on-device agents can invoke enabled tools without going through the SSE endpoint.

Both phases share infrastructure: AppFunctions Jetpack dependencies, a small shared schema mapper in `:core`, and a single runtime SDK gate (`Build.VERSION.SDK_INT >= 36`). The existing MCP server behaviour is unchanged on all SDK levels.

## Approach

**Approach 1 — Modular, mirror the existing pattern.**

- Phase 1 lives in one self-contained tool module that follows the same shape as `tools/externaltools` (configurator + repository + tool + Hilt DI module). Discovery uses `AppFunctionManager.observeAppFunctions(...)`; each discovered function is registered with `server.addTool(...)` and its handler forwards to `AppFunctionManager.executeAppFunction(...)`.
- Phase 2 keeps each tool module self-contained — its existing `McpTool` implementation stays, and a new `@AppFunction`-annotated class lives next to it. KSP-generated AppFunctions service registration is aggregated in the `app/` module. A single `AppFunctionConfiguration.Provider` in `MainApplication` collects every per-tool Hilt-bound functions class.
- Shared schema-mapping helper lives in `:core` next to `McpTool`.

Rejected alternatives:

- **Unify `McpTool` and `@AppFunction` (Approach 2)** — a bigger refactor of every tool module that wouldn't actually eliminate duplication, because the SDK 36 runtime gate forces `McpTool` to keep working independently on older devices.
- **New `:core-appfunctions` module (Approach 3)** — over-engineered for what's effectively one mapping function and one application-level configuration provider. Both fit comfortably in `:core` and `app/`.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                       Android device (API 36+)                      │
│                                                                     │
│   ┌─────────────────── phone-mcp app (this project) ────────────┐   │
│   │                                                             │   │
│   │   ┌─── McpServerService (Ktor + SSE) ───────────────────┐   │   │
│   │   │  Existing McpTools: sms, smsintent, camera,         │   │   │
│   │   │  contacts, location, sensor, files, ads, meta-tools │   │   │
│   │   │                                                     │   │   │
│   │   │  NEW: tools/appfunctions (Phase 1, inbound)         │   │   │
│   │   │  ┌──────────────────────────────────────────────┐   │   │   │
│   │   │  │ AppFunctionsTool : McpTool                   │   │   │   │
│   │   │  │  └─ AppFunctionsConfigurator                 │◀──┼───┼───┼─── via AppFunctionManager
│   │   │  │      ├─ discovers all AppFunctions on device │   │   │   │
│   │   │  │      ├─ registers each as server.addTool()   │   │   │   │
│   │   │  │      └─ forwards calls via executeAppFunction│   │   │   │
│   │   │  └──────────────────────────────────────────────┘   │   │   │
│   │   └─────────────────────────────────────────────────────┘   │   │
│   │                                                             │   │
│   │   NEW: Application implements AppFunctionConfiguration      │   │
│   │        .Provider (Phase 2, outbound — Hilt-wired)           │   │
│   │                                                             │   │
│   │   ┌──── Existing tool modules ────────────────────────┐     │   │
│   │   │ tools/sms      ─▶ @AppFunction sendSms(...)       │◀────┼───┼─── on-device Gemini /
│   │   │ tools/smsintent ─▶ @AppFunction sendSmsViaIntent  │     │   │    Assistant calls via
│   │   │ tools/contacts ─▶ @AppFunction listContacts(...)  │     │   │    AppFunctionManager
│   │   │ tools/camera   ─▶ @AppFunction takePhoto(...)     │     │   │
│   │   │ tools/location ─▶ @AppFunction getCurrentLocation │     │   │
│   │   │ tools/sensor   ─▶ @AppFunction sensor snapshot    │     │   │
│   │   │ tools/files    ─▶ @AppFunction list/info/read     │     │   │
│   │   │ tools/ads      ─▶ (skipped — promo-only)          │     │   │
│   │   └───────────────────────────────────────────────────┘     │   │
│   │                                                             │   │
│   │   res/xml/app_metadata.xml — describes app to LLM           │   │
│   │   AndroidManifest <property> referencing it                 │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│   Other apps publishing AppFunctions (Maps, Calendar, Notes…)       │
│   ─▶ discovered by AppFunctionsConfigurator                         │
└─────────────────────────────────────────────────────────────────────┘
```

**Skipped from Phase 2 (deliberate, documented):**

- `tools/ads` — displays ads, no end-user-meaningful action to expose to agents.
- `tools/externaltools` — already a meta-tool aggregating other apps' tools via Content Providers. Phase 1 subsumes it for AppFunctions-enabled apps.
- `tools/appfunctions` — the inbound bridge is a dynamic meta-tool, not a first-party phone capability to republish recursively.

## Module Structure

### Phase 1: new module `tools/appfunctions/`

```
tools/appfunctions/
├── build.gradle.kts                            # mcp.android.tool convention
├── src/main/AndroidManifest.xml                # no permissions; runtime-gated
└── src/main/java/se/premex/mcp/appfunctions/
    ├── configurator/
    │   ├── AppFunctionsConfigurator.kt         # interface
    │   └── AppFunctionsConfiguratorImpl.kt     # @Singleton — discovery + invocation
    ├── repositories/
    │   └── AppFunctionMetadataInfo.kt          # data class for one discovered function
    ├── tool/
    │   └── AppFunctionsTool.kt                 # McpTool implementation
    └── di/
        └── AppFunctionsModule.kt               # Hilt @Module @Binds @IntoSet
```

| Component | Responsibility |
|---|---|
| `AppFunctionsTool : McpTool` | `id = "app_functions"`, `name = "AppFunctions"`, `enabledByDefault = false` (off until user opts in, given the broad attack surface), `disclaim` warns: "Exposes every AppFunction registered on this device to the connected MCP client over the bearer-token-authenticated SSE channel." `configure(server)` delegates to the configurator. `requiredPermissions() = emptySet()`. |
| `AppFunctionsConfiguratorImpl` | Holds an injected `Context`. At `configure(server)` time: checks SDK gate, obtains `AppFunctionManager`, enumerates registered functions, calls `server.addTool(name, description, inputSchema) { request -> invokeAppFunction(...) }` once per function. |
| `AppFunctionMetadataInfo` | Our internal data class — a stable, test-friendly projection of the platform's `androidx.appfunctions.metadata.AppFunctionMetadata` (which the configurator gets from `observeAppFunctions`). Fields: `packageName: String`, `functionId: String`, `mcpToolName: String` (sanitized to `appfn__{package}__{functionId}` with non-alphanumerics → `_`), `description: String`, `inputSchemaJson: String`, `requiredFields: List<String>`. Built once per discovery; not persisted. |
| `AppFunctionsModule` | Hilt `@Module @InstallIn(SingletonComponent::class)`: `@Binds` configurator, `@Binds @IntoSet` the `McpTool`. |

### Phase 1 settings & app wiring

- `settings.gradle.kts`: `include(":tools:appfunctions")`
- `app/build.gradle.kts` dependencies: `implementation(project(":tools:appfunctions"))`

### Phase 2: changes to existing modules

| Module | Change |
|---|---|
| `app/` | `McpServerApplication` (the existing `@HiltAndroidApp` class at `app/src/main/java/se/premex/mcp/McpServerApplication.kt`) implements `AppFunctionConfiguration.Provider`. Adds `res/xml/app_metadata.xml`. `AndroidManifest.xml` adds the `<property android:name="android.app.appfunctions.app_metadata" android:resource="@xml/app_metadata" />` entry inside `<application>`. |
| `app/build.gradle.kts` | Adds `androidx.appfunctions`, `androidx.appfunctions.service`, KSP `androidx.appfunctions.compiler`; sets `ksp { arg("appfunctions:aggregateAppFunctions", "true") }`. |
| `build-logic` | New convention plugin `McpAndroidAppFunctionsConventionPlugin` (id `mcp.android.appfunctions`). Applied by any tool module that publishes `@AppFunction`s — adds the dependencies + KSP arg + `compileSdk = 37` requirement in one place. |
| `tools/sms` | Adds `SmsAppFunctions.kt` with `@AppFunction suspend fun sendSms(context: AppFunctionContext, to: String, body: String): String`. Delegates to existing `SmsSender`. Hilt-provided via the module's existing DI. |
| `tools/smsintent` | Adds `SmsIntentAppFunctions.kt` — same shape, delegates to `SmsIntentSender`. |
| `tools/contacts` | Adds `ContactsAppFunctions.kt` with `searchContacts(...)` returning typed contact results. |
| `tools/camera` | Adds `CameraAppFunctions.kt` with `takePhoto(context, lens: String = "back"): Uri`. Returns a content `Uri` to the captured image (AppFunctions has `Uri` as a native supported type). |
| `tools/location` | Adds `LocationAppFunctions.kt` returning a typed current-location result. |
| `tools/sensor` | Adds `SensorAppFunctions.kt` returning a one-shot typed snapshot with latest cached readings. |
| `tools/files` | Adds `FilesAppFunctions.kt` for root/directory listing, metadata, and reads capped at 250 KB. |

`tools/ads`, `tools/externaltools`, and the inbound `tools/appfunctions` meta-tool do not publish outbound functions (see Skipped section above).

### Wiring summary

- **One `AppFunctionService`** is implicit — the Jetpack library generates and registers it from the KSP-aggregated function set across modules. No service class to write ourselves; only the `app_metadata.xml` and the manifest property to declare the app's overall purpose.
- **`AppFunctionConfiguration.Provider`** in `McpServerApplication` collects every `@AppFunction`-containing class from each tool module via Hilt. The application uses `AppFunctionConfiguration.Builder().addEnclosingClassFactory(...)` calls — one per tool's functions class — built from injected Hilt instances.
- **Tool-state synchronization** in `ToolService` updates Android's runtime enabled state for each generated function ID whenever the corresponding Phone MCP tool switch changes. Static metadata defaults every function to disabled until the app loads the user's saved choices.

### Shared schema mapper (in `:core`)

A new file `core/src/main/java/se/premex/mcp/core/tool/AppFunctionSchemaMapper.kt` (~50 lines). Pure JVM, no Android dependencies. Converts AppFunctions' parameter metadata into MCP's `ToolSchema` (`properties: JsonObject`, `required: List<String>`). Used by `tools/appfunctions` for now; available to other consumers later.

## Data Flow

### A. Inbound: remote MCP client → bridged AppFunction (Phase 1)

```
1. Server start
   McpServerService starts.
   For each enabled McpTool: tool.configure(server).
   AppFunctionsTool.configure(server)
     ├─ if (Build.VERSION.SDK_INT < 36) → log, return (no tools registered)
     ├─ ctx.getSystemService<AppFunctionManager>()  // null on missing service → return
     ├─ manager.observeAppFunctions(searchSpec)     // emits metadata for all functions
     ├─ for each AppFunctionMetadata m:
     │    schema = AppFunctionSchemaMapper.toMcpToolSchema(m)
     │    server.addTool(
     │      name = "appfn__${m.packageName}__${m.functionId}",   // sanitized
     │      description = m.description,
     │      inputSchema = schema,
     │    ) { request -> invokeAppFunction(m, request.arguments) }
     └─ log "Registered N AppFunctions from K packages"

2. Tool invocation (remote MCP client over SSE)
   POST /sse → MCP server routes call to the lambda above.
   invokeAppFunction(m, args):
     ├─ params = AppFunctionSchemaMapper.toGenericDocument(args, m.parameterSchema)
     ├─ request = ExecuteAppFunctionRequest(m.packageName, m.functionId, params)
     ├─ result = manager.executeAppFunction(request)              // suspend
     ├─ on AppFunctionException → CallToolResult(TextContent("Error: ${e.message}"))
     └─ on success → CallToolResult(TextContent(result.toJsonString()))
```

**Discovery cadence:** snapshot at `configure(server)` time (matches `externaltools`). New AppFunctions installed after server start don't appear until the server restarts. Documented limitation; reactive discovery via `PackageManager.ACTION_PACKAGE_ADDED` is out of scope for this spec.

### B. Outbound: on-device Gemini / Assistant → our `@AppFunction` (Phase 2)

```
1. Build time
   KSP processor walks every @AppFunction in every module.
   Generates aggregated AppFunctionService + metadata:
     ─ assets/app_function_v2.xml
     ─ schema records consumed by AppSearch indexing on install

2. Install / first launch
   System AppSearch indexes our app's AppFunctions.
   McpServerApplication.appFunctionConfiguration provides Hilt-built instances:
     AppFunctionConfiguration.Builder()
       .addEnclosingClassFactory(SmsAppFunctions::class.java)       { hiltSms }
       .addEnclosingClassFactory(SmsIntentAppFunctions::class.java) { hiltSmsIntent }
       .addEnclosingClassFactory(ContactsAppFunctions::class.java)  { hiltContacts }
       .addEnclosingClassFactory(CameraAppFunctions::class.java)    { hiltCamera }
       .build()

3. Invocation
   On-device agent calls AppFunctionManager.executeAppFunction(
       package = "se.premex.mcp",
       function = "sendSms",
       parameters = {"to": "...", "body": "..."}
   )
   System routes to our (KSP-generated) AppFunctionService
     → resolves SmsAppFunctions instance via the configuration provider
     → calls suspend fun sendSms(ctx: AppFunctionContext, to: String, body: String): String
         └─ withContext(Dispatchers.IO) { smsSender.send(to, body); "ok" }
   Errors → throw AppFunctionException subclasses
     (e.g. AppFunctionPermissionRequiredException for missing SEND_SMS).
```

**Permission model:** Phase 2 functions delegate to the same repositories the existing `McpTool`s use. Runtime permissions for SMS, contacts, camera, location, notifications, and shared media are already declared in each tool module's `AndroidManifest.xml`. If the user hasn't granted them, the function reports `AppFunctionPermissionRequiredException` so the calling agent gets a structured error.

**Auth scope difference — important:** the MCP server's bearer-token gate does **not** apply to Phase 2. On-device agents invoking our `@AppFunction`s go through the platform's AppFunctions service, not our SSE endpoint. The platform decides who can call them (Gemini, Assistant, third-party assistants holding `EXECUTE_APP_FUNCTIONS`). Phone MCP additionally synchronizes Android's enabled state for every function with its existing tool switch, so the user's opt-in applies to both interfaces.

## Error Handling

| # | Failure | Where | Handling |
|---|---|---|---|
| 1 | Device runs Android < 16 (API < 36) | `AppFunctionsConfiguratorImpl.configure()` | Log once at INFO ("AppFunctions unavailable on SDK < 36, skipping"). Register no tools. `AppFunctionsTool` stays visible in the UI but produces zero bridged tools. No exception. |
| 2 | `AppFunctionManager` system service absent on SDK 36+ (vendor cut, GMS variant) | same | `getSystemService()` returns null → log warning, register no tools, continue. |
| 3 | Discovery throws (`SecurityException`, `RemoteException`) | configurator | Catch, log with context, register no tools — other tools must still configure. |
| 4 | Per-function schema mapping fails (unrecognized parameter type) | `AppFunctionSchemaMapper.toMcpToolSchema` | Skip just that function with a warning, continue registering the rest. Don't poison the whole batch. |
| 5 | Remote MCP client invokes a bridged tool whose target app has been uninstalled | invocation lambda | `executeAppFunction` throws (likely `AppFunctionFunctionNotFoundException`). Catch, return `CallToolResult(TextContent("Function no longer available; restart the MCP server to refresh the tool list."))`. Don't crash the SSE loop. |
| 6 | `executeAppFunction` returns an `AppFunctionException` (permission denied, timeout, validation) | invocation lambda | Catch, surface message in `CallToolResult` text content. Match the existing `externaltools` pattern of returning errors as content rather than JSON-RPC errors. |
| 7 | Phase 2: `@AppFunction sendSms(...)` called without `SEND_SMS` granted | function body | Underlying `SmsSender` throws `SecurityException`. Wrap and rethrow as `AppFunctionPermissionRequiredException` so the platform / agent can prompt. |
| 8 | Phase 2: `@AppFunction` receives malformed parameters from the platform | KSP-generated dispatch | Jetpack validates against declared types before calling our function; type errors surface to the caller as `AppFunctionInvalidArgumentException` automatically. |
| 9 | KSP build failure (annotation typo, unsupported return type) | build time | Compile error — caught by CI; not a runtime concern. |

**Logging tag convention:** `AppFunctionsConfig` for the configurator, matching the existing `TAG = "ExternalToolsConfigImpl"` style. Discovery and invocation log INFO on success, WARN on per-item failure, ERROR only on framework-level failure.

**No silent fallback to "tool succeeded" on failure** — every error path returns either a structured `AppFunctionException` (Phase 2) or a `CallToolResult` containing failure text (Phase 1). Callers always know something went wrong.

## Testing

| Layer | What | How |
|---|---|---|
| **Unit (pure JVM)** | `AppFunctionSchemaMapper` — converts AppFunctions parameter metadata ↔ MCP `ToolSchema`. | JUnit. Fixture inputs for each supported type (`String`, `Int`, `Boolean`, `List<String>`, nested `@AppFunctionSerializable`, optional/nullable, defaults); assert mapped JSON schema is correct. One negative test per documented "skipped function" path (unsupported type → mapper returns null with reason). Lives in `core/src/test/`. |
| **Instrumented (API 36 emulator)** | End-to-end of both directions in the same project. | `app/src/androidTest/`. Two tests:<br>1. **Inbound:** start `McpServerService`, hit `/sse` via an in-process Ktor client, assert the bridged tool list is non-empty (because our own app's `@AppFunction`s are discoverable on the same device), call one, assert success.<br>2. **Phase 2 standalone:** call `AppFunctionManager.executeAppFunction("se.premex.mcp", "sendSms", …)` directly from the test, with a mocked `SmsSender` injected via Hilt test rules; assert it was invoked. |
| **ADB smoke (manual / release checklist)** | Sanity check on a real device that the app's `@AppFunction`s are discoverable by the system. | `adb shell cmd app_function list-app-functions \| grep se.premex.mcp` returns our functions. `adb shell cmd app_function execute-app-function --package se.premex.mcp --function sendSmsViaIntent --parameters '{"to":"+1234","body":"test"}'` returns ok. Documented as a release-check item, not a CI gate (requires an emulator with the AppFunctions service installed). |

**Deliberately not tested:**

- Mocking `AppFunctionManager` for unit tests — it's a system service, final, API 36+ only. Mocking it produces tests that validate the mock more than the code. The configurator's logic is thin (delegate + map + register); instrumented tests cover its real behaviour.
- Cross-version compat (`SDK_INT < 36` path) — covered by a single unit test on a `FakeBuildVersion` boundary check, not by running on multiple API levels.
- Discovery of third-party apps' AppFunctions during the instrumented test. Too brittle to depend on Maps / Calendar / Notes being installed on the emulator. Our own Phase 2 functions serve as the test corpus.

**Pre-existing gap noted but out of scope:** `tools/externaltools` currently has no tests. The Hilt test rules + in-process Ktor client added here will be reusable for backfilling those later.

## Open Decisions Made During Brainstorming

These are choices that were made via reasonable-default rather than explicit user selection. Each is small enough to revisit in implementation if needed.

- **Granularity of user control over the inbound bridge:** coarse (one toggle for the whole bridge, off by default with a disclaim). Per-app or per-function allowlists are deferrable enhancements.
- **Discovery cadence (Phase 1):** snapshot at server start. Reactive discovery on `ACTION_PACKAGE_ADDED` deferred.
- **Schema mapper location:** `:core` rather than a new `:core-appfunctions` module. Mapper is small.
- **Phase 2 tool selection:** `sms`, `smsintent`, `contacts`, `camera`, `location`, `sensor`, and
  `files`. The later location and files modules were added after the original design; sensor was
  revisited after confirming that its public MCP operation is a one-shot snapshot. `ads` and
  dynamic meta-tools remain excluded with reasons documented above.

## Phasing

Implementation can be sequenced as two plans:

1. **Plan 1 — Phase 1 (inbound bridge).** Self-contained: new `tools/appfunctions/` module, `:core` schema mapper, build-logic plumbing for AppFunctions Jetpack dep on this one tool module, settings.gradle + app dependency. Delivers an MCP-callable surface immediately. Does not require any Phase 2 work.
2. **Plan 2 — Phase 2 (outbound dual-publish).** Touches every Phase 2 tool module, the `app/` module, build-logic. Bigger surface area but every module change is mechanical and follows the same pattern.

Plan 1 should ship first to validate the AppFunctions dependency / KSP / SDK gating in isolation before fanning out the change across every tool module in Plan 2.
