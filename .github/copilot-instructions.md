# Android MCP Server - Instructions

## Project Overview

This is a multi-module Android application that implements a **Server-Sent Events (SSE) MCP (Model Context Protocol) server**. The server enables AI assistants to connect and interact with Android device capabilities through a standardized protocol.

### Key Features
- **SSE-based MCP Server**: Runs as an Android foreground service with Ktor
- **Modular Tool Architecture**: Each device capability is a separate, pluggable module
- **Dynamic Tool Management**: Tools can be enabled/disabled at runtime with user consent
- **Privacy-Focused**: Each tool requires explicit user permission with clear disclaimers
- **Authentication**: Bearer token-based authentication for secure connections
- **External Tool Support**: Third-party apps can expose MCP tools via Content Providers

## Architecture Overview

The application follows a **clean, modular architecture** with clear separation of concerns:

```
android-mcp-server/
├── app/                    # Main application module
├── core/                   # Core interfaces and contracts
├── mcp-provider/          # Library for external tool integration
├── externalmcptool/       # Example external tool implementation
└── tools/                 # Individual tool modules
    ├── sms/
    ├── camera/
    ├── contacts/
    ├── sensor/
    ├── ads/
    ├── smsintent/
    └── externaltools/
```

### Core Modules

#### 1. `/app` - Main Application Module
**Purpose**: Contains the UI, server logic, and service management

**Key Components**:
- `McpServerService`: Foreground service that runs the Ktor SSE server
- `McpServerApplication`: Application class with Hilt setup
- `MainActivity`: Compose UI for server control and tool management
- `ToolService`: Manages tool states and preferences
- `ToolPreferencesRepository`: DataStore-based persistence for tool states
- `AuthRepository`: Manages authentication tokens

**Dependencies**: All tool modules, core, mcp-provider

#### 2. `/core` - Core Interfaces
**Purpose**: Defines the `McpTool` interface that all tools implement

**Key Interface**:
```kotlin
interface McpTool {
    val id: String                          // Unique identifier
    val name: String                        // Display name
    val enabledByDefault: Boolean           // Default enabled state
    val disclaim: String?                   // Privacy/warning disclaimer
    fun configure(server: Server)           // Register with MCP server
    fun requiredPermissions(): Set<String>  // Android permissions needed
}
```

**Type**: Java library module (not Android library)

#### 3. `/mcp-provider` - External Tool SDK
**Purpose**: Library that external Android apps can use to expose MCP tools

**Key Classes**:
- `McpProvider`: Base class for creating external tool providers
- `ToolInfo`, `Tools`: Data classes for tool metadata
- `ToolInput`: Various input type definitions

**Integration Method**: Content Provider with custom authority

#### 4. `/tools/*` - Tool Modules
**Purpose**: Individual modules implementing specific device capabilities

**Current Tools**:
- `sms`: Send SMS messages
- `smsintent`: Send SMS via Intent (no permission required)
- `camera`: Access camera and take photos
- `contacts`: Read contacts
- `sensor`: Access device sensors
- `ads`: Display ads (example tool)
- `externaltools`: Discovers and integrates external MCP tools

## Tool Module Structure

Each tool module follows a **consistent, standardized structure** for maintainability:

### Standard Directory Layout
```
tools/{LOWERCASE_TOOL_NAME}/
├── build.gradle.kts
├── src/main/
    ├── AndroidManifest.xml
    └── java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/
        ├── configurator/
        │   ├── {Toolname}ToolConfigurator.kt      # Interface
        │   └── {Toolname}ToolConfiguratorImpl.kt  # Implementation
        ├── di/
        │   └── {Toolname}Tool.kt                  # Hilt module
        ├── repositories/
        │   ├── {Toolname}Info.kt                  # Data class
        │   ├── {Toolname}Repository.kt            # Interface
        │   └── {Toolname}RepositoryImpl.kt        # Implementation
        └── tool/
            └── {Toolname}Tool.kt                  # McpTool implementation
```

### Two Main Architectural Patterns

#### Pattern 1: Configurator-Based (Recommended for complex tools)
**Example**: camera, contacts

**Structure**:
- **Configurator Interface**: Defines `configure(server: Server)`
- **Configurator Implementation**: Registers MCP tools with the server
- **Repository**: Handles Android API interactions
- **Tool Class**: Implements `McpTool`, delegates to configurator

**When to use**: Tools with multiple MCP endpoints, complex logic, or significant Android API interaction

#### Pattern 2: Direct Configuration (Simpler tools)
**Example**: sms

**Structure**:
- **Extension Function**: `appendXxxTools(server: Server, dependency)`
- **Service/Repository**: Business logic
- **Tool Class**: Implements `McpTool`, calls extension function

**When to use**: Simple tools with single or few MCP endpoints

### Key Files Explained

#### 1. `build.gradle.kts`
Standard configuration for all tool modules:

```kotlin
plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.{tool_name}"
}

dependencies {
    // Additional dependencies beyond what the convention plugin provides
    // Example: Lifecycle for lifecycle-aware operations
    implementation(libs.androidx.lifecycle.process)
}
```

**Note**: The `mcp.android.tool` convention plugin automatically provides:
- compileSdk = 36, minSdk = 24
- Kotlin JVM toolchain 21 (with foojay auto-download)
- Hilt dependency injection setup
- Core MCP SDK and Ktor dependencies
- Standard Android libraries (core-ktx, appcompat, material)
- Test dependencies (junit, espresso)
- No need for manual `defaultConfig`, `kotlin { jvmToolchain }`, or `compileOptions` blocks

#### 2. `AndroidManifest.xml`
Minimal manifest, mainly for permissions:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Declare required permissions -->
    <uses-permission android:name="android.permission.SEND_SMS"/>
</manifest>
```

#### 3. `{Toolname}Tool.kt` (in di/ directory)
**Hilt module** that provides the tool implementation:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object {Toolname}ToolModule {
    
    @Provides
    @Singleton
    @IntoSet  // Critical: adds to Set<McpTool>
    fun provide{Toolname}Tool(
        dependency: DependencyType
    ): McpTool {
        return {Toolname}Tool(dependency)
    }
    
    @Provides
    @Singleton
    fun provide{Toolname}Repository(
        @ApplicationContext context: Context
    ): {Toolname}Repository {
        return {Toolname}RepositoryImpl(context)
    }
}
```

**Important**: The `@IntoSet` annotation is crucial - it adds the tool to the `Set<McpTool>` that the app module injects.

#### 4. `{Toolname}Tool.kt` (in tool/ directory)
**McpTool implementation**:

```kotlin
class {Toolname}Tool(
    private val dependency: DependencyType
) : McpTool {
    override val id: String = "toolid"
    override val name: String = "Display Name"
    override val enabledByDefault: Boolean = false
    override val disclaim: String? = """
        PRIVACY WARNING: [Explain what access this tool provides]
        
        By enabling this tool, you grant permission to:
        • [List specific capabilities]
        • [List data access]
        
        You acknowledge that:
        • [User responsibilities]
        • [Compliance requirements]
    """.trimIndent()
    
    override fun configure(server: Server) {
        // Either call configurator or extension function
        dependency.configure(server)
    }
    
    override fun requiredPermissions(): Set<String> {
        return setOf(
            android.Manifest.permission.SOME_PERMISSION
        )
    }
}
```

#### 5. Tool Registration with MCP Server
The configurator/extension function registers tools:

```kotlin
server.addTool(
    name = "phone_tool_name",  // Prefix with "phone_"
    description = """
        Detailed description of what the tool does.
        Include parameters, expected behavior, and any limitations.
    """.trimIndent(),
    inputSchema = Tool.Input(
        properties = buildJsonObject {
            putJsonObject("param_name") {
                put("type", "string")
                put("description", "Parameter description")
            }
        },
        required = listOf("param_name")
    )
) { request ->
    // Extract parameters
    val param = request.arguments["param_name"]?.jsonPrimitive?.content
        ?: return@addTool CallToolResult(
            content = listOf(TextContent("Error: param_name is required"))
        )
    
    // Execute logic
    val result = repository.doSomething(param)
    
    // Return result
    CallToolResult(
        content = listOf(TextContent(result))
    )
}
```

## Technology Stack

### Core Technologies
- **Language**: Kotlin (version 2.1.21, JVM toolchain 21)
- **Build System**: Gradle with Kotlin DSL
- **Dependency Injection**: Hilt (Dagger)
- **Async**: Kotlin Coroutines
- **Data Persistence**: DataStore (Preferences)

### Networking & Protocol
- **HTTP Server**: Ktor Server (CIO engine)
- **SSE Support**: Ktor SSE plugin
- **Authentication**: Ktor Auth plugin (Bearer tokens)
- **CORS**: Ktor CORS plugin
- **MCP Protocol**: `io.modelcontextprotocol:kotlin-sdk:0.5.0`

### UI
- **Framework**: Jetpack Compose
- **Navigation**: Hilt Navigation Compose
- **Material Design**: Material 3

### Android
- **Min SDK**: 24 (Android 7.0)
- **Target/Compile SDK**: 36
- **Camera**: CameraX library
- **Lifecycle**: Lifecycle Process for app state

## Development Guidelines

### Creating a New Tool Module

1. **Create directory structure** following the standard layout
2. **Add to `settings.gradle.kts`**: `include(":tools:newtool")`
3. **Create `build.gradle.kts`** with standard dependencies
4. **Implement the 4-7 required files** (see structure above)
5. **Choose architecture pattern** (Configurator vs Direct)
6. **Add AndroidManifest.xml** with required permissions
7. **Test tool registration** in the app UI

### Naming Conventions

- **Module names**: lowercase, e.g., `tools/camera`
- **Package names**: `se.premex.mcp.{toolname}`
- **Class names**: PascalCase, e.g., `CameraTool`
- **Tool IDs**: lowercase, e.g., `"camera"`
- **MCP tool names**: snake_case with `phone_` prefix, e.g., `"phone_take_photo"`
- **File names**: Match class names exactly

### Code Style

- **Indentation**: 4 spaces
- **Null safety**: Prefer null-safe operators (`?.`, `?:`)
- **Coroutines**: Use `suspend` functions for async operations
- **Error handling**: Return `CallToolResult` with error messages, don't throw
- **Documentation**: KDoc comments for public APIs
- **Logging**: Use `android.util.Log` with consistent tags

### Permissions & Privacy

**Critical Guidelines**:
- Always declare permissions in `AndroidManifest.xml`
- Return required permissions in `requiredPermissions()`
- Set `enabledByDefault = false` for sensitive tools
- Provide clear, comprehensive `disclaim` messages
- Explain what data is accessed and how it's used
- State user responsibilities and AI service implications

### Testing Considerations

- Tool modules should be testable independently
- Repositories should have interfaces for mocking
- Consider creating test implementations of repositories
- Test permission handling separately from business logic

## Server Operation

### Service Lifecycle
1. **Startup**: `McpServerService` starts as foreground service
2. **Notification**: Persistent notification with server status
3. **Server Creation**: Ktor server on port 3001 (configurable)
4. **Tool Loading**: Loads enabled tools from DataStore
5. **Authentication**: Validates bearer tokens from `AuthRepository`
6. **SSE Endpoint**: `/sse` endpoint for MCP transport
7. **Shutdown**: Clean shutdown on service stop

### Authentication Flow
- Tokens generated in app UI
- Stored in DataStore
- Validated on each SSE connection
- Bearer token required in `Authorization` header

### Tool State Management
- `ToolService` manages enabled/disabled state
- Persisted to DataStore via `ToolPreferencesRepository`
- State flows observed by UI
- Changes take effect on next server restart

## External Tool Integration

Third-party apps can expose MCP tools by:

1. **Depend on `mcp-provider` module**
2. **Extend `McpProvider` class**
3. **Implement `getToolInfo()` and `executeToolRequest()`**
4. **Register provider in AndroidManifest.xml**
5. **Use authority**: `${applicationId}.authorities.McpProvider`

The `externaltools` module automatically discovers and integrates these tools.

## Common Patterns

### Repository Pattern
```kotlin
interface XRepository {
    suspend fun getData(): Result<Data>
}

class XRepositoryImpl(context: Context) : XRepository {
    override suspend fun getData(): Result<Data> = 
        withContext(Dispatchers.IO) {
            // Android API calls
        }
}
```

### Hilt Injection in Tools
- Repositories are `@Singleton`
- Tools are provided `@IntoSet`
- Use `@ApplicationContext` for Context injection
- Install in `SingletonComponent`

### Error Handling in MCP Tools
```kotlin
try {
    val result = repository.getData()
    CallToolResult(content = listOf(TextContent(result)))
} catch (e: Exception) {
    CallToolResult(
        content = listOf(TextContent("Error: ${e.message}"))
    )
}
```

### Backwards Compatibility (API 24+)
When using APIs introduced after API 24, always use version checks:

```kotlin
// NotificationChannel (API 26+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val channel = NotificationChannel(...)
    notificationManager.createNotificationChannel(channel)
}

// SmsManager.getDefault() vs context.getSystemService (API 31+)
val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    context.getSystemService(SmsManager::class.java)
} else {
    @Suppress("DEPRECATION")
    SmsManager.getDefault()
}
```

**Key APIs to check**:
- **API 26 (O)**: NotificationChannel, NotificationManager.IMPORTANCE_*
- **API 31 (S)**: SmsManager from system service
- **API 33 (TIRAMISU)**: POST_NOTIFICATIONS permission

**Safe to use (available in API 23+)**:
- `PendingIntent.FLAG_IMMUTABLE`
- `PendingIntent.FLAG_UPDATE_CURRENT`


---

**Note**: For detailed instructions on creating a new module, see `/tools/CREATE_MODULE_INSTRUCTIONS.md`
