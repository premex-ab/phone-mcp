# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android MCP Server - an SSE-based Model Context Protocol server running as an Android foreground service. Enables AI assistants to interact with Android device capabilities (files, camera, contacts, sensors, location, SMS) through Ktor on port 3001 with bearer token authentication. Clients connect either over the local network or remotely through the hosted relay at https://phonemcp.ai (see *Remote access* below).

## Build Commands

```bash
./gradlew assembleFullDebug          # Build debug APK (full flavor, includes SMS tool)
./gradlew assemblePlayDebug          # Build debug APK (Play Store flavor, no SMS)
./gradlew test                       # Run unit tests
./gradlew lint                       # Run Android lint
./gradlew testDebugUnitTest          # Run tests for specific variant
./gradlew connectedAndroidTest       # Instrumented tests (requires device/emulator)
./gradlew assembleFullRelease -PenableFirebase=true  # Release with Firebase
```

CI runs: `assembleFullDebug`, `lint`, `test` (JDK 21 Temurin, macOS `tart` runner). **Lint is a merge gate but is NOT part of `assemble` — run `./gradlew lint` locally before pushing** or CI will fail on things a local build never showed.

## Architecture

Multi-module Android app. Kotlin, Gradle Kotlin DSL, JVM toolchain 21, min SDK 24, target SDK 36.

### Module layout

- **`app/`** - Main module: Compose UI, Ktor SSE server (`McpServerService`), auth (`AuthRepository`), tool state management (`ToolPreferencesRepository`), remote access (`remote/` package), connection guide UI (`ui/ConnectionGuide.kt`). All tool modules are dependencies here.
- **`core/`** - JVM library (not Android) defining the `McpTool` interface that all tools implement.
- **`mcp-provider/`** - Android library SDK for third-party apps to expose MCP tools via ContentProvider.
- **`externalmcptool/`** - Example external tool app (calculator, text reversal) demonstrating the provider SDK.
- **`tools/`** - Each subdirectory is an independent tool module: `sms`, `smsintent`, `camera`, `contacts`, `sensor`, `location`, `files`, `ads`, `externaltools`.
- **`build-logic/`** - Gradle convention plugins. Key plugin: `mcp.android.tool` auto-configures SDK versions, Hilt, core dependency, MCP SDK, and standard deps for tool modules.

### Key design patterns

**McpTool interface** (`core`): Every tool implements `id`, `name`, `enabledByDefault`, `disclaim`, `configure(server)`, `requiredPermissions()`. Tools are collected via Hilt `@IntoSet` into `Set<McpTool>`. Privacy-sensitive tools (e.g. `files`) set `enabledByDefault = false` and a `disclaim` text that the UI shows as a consent dialog before enabling.

**Two tool patterns:**
1. **Configurator pattern** (camera, contacts, files): Tool delegates to a `{Name}ToolConfigurator` interface/impl that handles `server.addTool()` registration. Use for complex tools with multiple endpoints.
2. **Direct pattern** (sms): Extension function registers tools directly. Use for simple tools.

**Standard tool module structure:**
```
tools/{name}/
  build.gradle.kts              # Uses `mcp.android.tool` convention plugin
  src/main/AndroidManifest.xml  # Required permissions
  src/main/java/se/premex/mcp/{name}/
    configurator/               # Optional: ToolConfigurator interface + impl
    di/                         # Hilt module: @Provides @Singleton @IntoSet
    repositories/               # Data access layer (interface + impl)
    tool/                       # McpTool implementation
```

**Build flavors:** `full` (sideload/GitHub: includes `:tools:sms`, **no remote access**) and `play` (Play Store: remote access + Play Billing, no direct SMS). Remote is gated by `BuildConfig.REMOTE_ACCESS`, set per flavor in `build-logic` — its subscription can only be sold through Play Billing, so sideloaded builds are local-network only. Combined with `debug`/`release` build types. **`full` has `applicationIdSuffix = ".full"`** — so `se.premex.mcp` (play) and `se.premex.mcp.full` can be installed side by side. Both default to port 3001, so running both servers at once fails the port preflight (by design, with an error notification).

## Remote access (phonemcp.ai)

The app can hold an outbound WSS tunnel to the PhoneMCP relay, giving the phone a public HTTPS MCP endpoint with OAuth 2.1 + single-use pairing-code authorization. The wire protocol is documented in `PROTOCOL.md`. The relay server itself is proprietary (private repo) — the hosted relay is the product that funds the open-source app; local-network use is free and ungated forever.

App-side pieces (`app/src/main/java/se/premex/mcp/remote/`):
- `RemoteAccessRepository` — DataStore-persisted state. The relay URL is the constant `RemoteAccessConfig.RELAY_URL` and is deliberately **not user-configurable**.
- `TunnelClient` — outbound WebSocket, reconnect with backoff, forwards requests to `127.0.0.1:<port>` and replaces the `Authorization` header with the local token (the relay never sees it).
- `TunnelStatusRepository` — bridges the tunnel's connected-state from the service to the UI.
- `RemoteAccessViewModel` — enable/registration (sends a SHA-256 of `ANDROID_ID` as `trialAnchor` so the free trial survives clear-data re-registrations), pairing codes, paired-client list + revoke, entitlement status, billing.
- `BillingManager` — Play Billing for the subscription product **`remote_access`**. A purchase is only trusted after the relay verifies its token server-side; only then is it acknowledged towards Play. Sideloaded builds without Play simply never see the product.

UX model (`ui/ConnectionGuide.kt`): the home-screen guide has two modes — **Remote (default)** and **Local network** — with per-client (Claude Code / Claude Desktop / Other) numbered steps: 1) run command / paste URL, 2) the browser asks for a pairing code, 3) tap *Show pairing code*. Already-paired clients reconnect automatically (relay refresh tokens rotate); a new code is needed only per new client.

## UI & theme

Static brand color scheme (blue derived from phonemcp.ai's `#4C8DFF`, light + dark) in `ui/theme/`. Dynamic color is intentionally disabled: wallpaper-derived neutrals made selected/enabled states invisible. Main screen: status hero card (tinted `tertiaryContainer` green while running) → connection guide card → Tools section with switches. `Test connection` + Wi-Fi warnings live in the guide's Local tab only.

## Hard-won gotchas

- **SSE handlers must stay suspended.** Ktor closes the SSE response when the handler returns; `McpServerService`'s `/sse` route awaits a `sessionClosed` deferred completed in `server.onClose`. Returning early silently breaks all server→client MCP messages.
- **Ktor CIO binds asynchronously** after `start(wait = false)` — a taken port used to surface as an *uncaught coroutine exception that killed the whole app* (and START_STICKY relaunched it into a crash loop). `ensurePortAvailable()` probes the port synchronously first; keep it.
- **`java.time` needs API 26**; minSdk is 24 and there is no core-library desugaring. Use `SimpleDateFormat`/epoch math in app code. Lint (CI gate) catches this; local `assemble` does not.
- Compose `@Preview` cannot resolve `hiltViewModel()` — preview-only paths must avoid it (see `LocalInspectionMode` guard in `ConnectionGuide`).

## Naming Conventions

- Module directories: lowercase (`tools/camera`)
- Package: `se.premex.mcp.{toolname}`
- MCP tool names registered with server: snake_case with `phone_` prefix (e.g., `phone_take_photo`)
- Tool IDs: lowercase (e.g., `camera`)

## Creating a New Tool

1. Create `tools/{name}/` directory following the standard structure above
2. `build.gradle.kts`: use `alias(libs.plugins.mcp.android.tool)` convention plugin, set `namespace = "se.premex.mcp.{name}"`
3. Implement `McpTool`, repository, DI module (with `@IntoSet`), and optionally a configurator
4. Add `include(":tools:{name}")` to `settings.gradle.kts`
5. Add `implementation(project(":tools:{name}"))` to `app/build.gradle.kts`

See `tools/CREATE_MODULE_INSTRUCTIONS.md` for detailed templates.

## Dependencies & Versions

Managed via version catalog at `gradle/libs.versions.toml`. Key deps: Ktor (server CIO + SSE + auth + CORS, client CIO + websockets for the tunnel), `io.modelcontextprotocol:kotlin-sdk`, Hilt/Dagger, Jetpack Compose + Material 3, CameraX, DataStore, Play Billing (`billing-ktx`), Firebase (conditional via `-PenableFirebase=true`).

## API Compatibility

Min SDK 24 requires version checks for newer APIs:
- API 26 (O): `NotificationChannel`, all of `java.time`
- API 31 (S): `SmsManager` from system service
- API 33 (TIRAMISU): `POST_NOTIFICATIONS` permission, `READ_MEDIA_*` permissions (files tool)
