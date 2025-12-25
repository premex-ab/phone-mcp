# Android MCP Server

A powerful, modular Android application that implements a **Server-Sent Events (SSE) Model Context Protocol (MCP) server**. This application enables AI assistants to safely and securely interact with Android device capabilities through a standardized protocol.

[![Get it on Google Play](https://play.google.com/intl/en_US/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=se.premex.mcp)

## Features

✨ **Core Capabilities**
- 🔌 **SSE-Based MCP Server**: Runs as an Android foreground service using Ktor
- 🧩 **Modular Architecture**: Pluggable tool modules for different device capabilities
- 🔐 **Privacy-Focused**: Each tool requires explicit user permission with clear disclaimers
- 🔑 **Bearer Token Authentication**: Secure connections with authentication tokens
- 🎛️ **Dynamic Tool Management**: Enable/disable tools at runtime with user consent
- 🤝 **External Tool Support**: Third-party apps can expose MCP tools via Content Providers

## Built-in Tools

- **📱 SMS**: Send SMS messages (requires permission)
- **📱 SMS Intent**: Send SMS via Intent (no permission required)
- **📷 Camera**: Access camera and take photos
- **👥 Contacts**: Read contacts list
- **📡 Sensor**: Access device sensors (accelerometer, gyroscope, etc.)
- **🎯 Ads**: Display ads (example tool)
- **🔌 External Tools**: Auto-discover and integrate tools from other apps

## Quick Start

### Installation

1. **Download from Google Play**: [Available on Google Play Store](https://play.google.com/store/apps/details?id=se.premex.mcp)
2. **Or build from source**:
   ```bash
   git clone https://github.com/yourusername/android-mcp-server
   cd android-mcp-server
   ./gradlew installDebug
   ```

### Setting Up the Server

1. **Launch the app** and grant necessary permissions
2. **Enable tools** you want to expose:
   - Review the disclaimer for each tool
   - Toggle individual tools on/off
3. **Start the server** - Get your authentication token
4. **Connect** your MCP client to `http://your-device:3001/sse` with the bearer token

### Connecting from an MCP Client

```bash
# Example using curl with SSE
curl -H "Authorization: Bearer YOUR_TOKEN" \
     http://your-device:3001/sse
```

## Architecture

The application follows a clean, modular architecture with clear separation of concerns:

```
android-mcp-server/
├── app/                    # Main application module
├── core/                   # Core interfaces (McpTool)
├── mcp-provider/          # Library for external tool integration
├── externalmcptool/       # Example external tool
└── tools/                 # Individual tool modules
    ├── sms/
    ├── camera/
    ├── contacts/
    ├── sensor/
    ├── smsintent/
    ├── ads/
    └── externaltools/
```

### Key Modules

#### `/app` - Main Application
Contains the UI, server logic, and service management:
- `McpServerService`: Foreground service running the Ktor SSE server
- `MainActivity`: Jetpack Compose UI for server control
- `ToolService`: Manages tool states and preferences
- `AuthRepository`: Manages authentication tokens

#### `/core` - Core Interfaces
Defines the `McpTool` interface that all tools implement:
```kotlin
interface McpTool {
    val id: String
    val name: String
    val enabledByDefault: Boolean
    val disclaim: String?
    fun configure(server: Server)
    fun requiredPermissions(): Set<String>
}
```

#### `/mcp-provider` - External Tool SDK
Library for creating external tool providers that third-party apps can use.

#### `/tools/*` - Tool Modules
Individual modules implementing specific device capabilities, each following a standard architecture pattern.

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.1.21, JVM 21 |
| **Build System** | Gradle with Kotlin DSL |
| **DI** | Hilt (Dagger) |
| **Async** | Kotlin Coroutines |
| **HTTP Server** | Ktor Server (CIO engine) |
| **MCP Protocol** | io.modelcontextprotocol:kotlin-sdk |
| **UI** | Jetpack Compose + Material 3 |
| **Database** | DataStore (Preferences) |
| **Camera** | CameraX |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |

## Development

### Prerequisites

- Android Studio (latest stable or canary)
- Java/Kotlin toolchain (JVM 21)
- Android SDK 36
- Minimum SDK 26 for testing

### Building

```bash
# Clone the repository
git clone https://github.com/yourusername/android-mcp-server
cd android-mcp-server

# Build the project
./gradlew build

# Install on device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

### Creating a New Tool Module

Tools follow a standardized structure. To create a new tool:

1. **Create module structure**:
   ```bash
   mkdir -p tools/newtool/src/main/{java/se/premex/mcp/newtool/{configurator,di,repositories,tool},AndroidManifest.xml}
   ```

2. **Implement required files**:
   - `build.gradle.kts` - Module build configuration
   - `AndroidManifest.xml` - Permissions declaration
   - `NewtoolTool.kt` - McpTool implementation
   - `NewtoolToolModule.kt` - Hilt module
   - `NewtoolRepository.kt` & `NewtoolRepositoryImpl.kt` - Data access
   - `NewtoolToolConfigurator.kt` & `NewtoolToolConfiguratorImpl.kt` - MCP configuration

3. **Register in `settings.gradle.kts`**:
   ```kotlin
   include(":tools:newtool")
   ```

4. **Add to app dependencies** in `app/build.gradle.kts`

See [CREATE_MODULE_INSTRUCTIONS.md](tools/CREATE_MODULE_INSTRUCTIONS.md) for detailed step-by-step instructions.

## Security & Privacy

### Important Principles

⚠️ **Privacy First**
- Each tool requires explicit user permission
- Clear disclaimers explain what data is accessed
- No data collection or transmission without user consent
- Users can disable tools at any time

🔐 **Security**
- Bearer token-based authentication
- HTTPS/TLS support
- Permission verification before tool execution
- Secure credential storage using Android Keystore

### Best Practices

1. Always declare required permissions in `AndroidManifest.xml`
2. Implement `requiredPermissions()` accurately
3. Set `enabledByDefault = false` for sensitive tools
4. Provide comprehensive `disclaim` messages
5. Validate all user inputs before processing
6. Handle errors gracefully without exposing sensitive data

## Configuration

### Server Settings

The server can be configured through the app UI:
- **Port**: Configurable (default: 3001)
- **Token Management**: Generate/revoke tokens
- **Tool Management**: Enable/disable tools with permission verification
- **Persistent Notification**: Shows server status

## API Reference

### MCP Endpoints

#### POST /sse
Server-Sent Events endpoint for MCP transport
- **Auth**: Bearer token required
- **Headers**: `Authorization: Bearer YOUR_TOKEN`

### Tool Format

Tools are registered with the following schema:
```json
{
  "name": "phone_tool_name",
  "description": "Tool description",
  "inputSchema": {
    "type": "object",
    "properties": {
      "param": {
        "type": "string",
        "description": "Parameter description"
      }
    },
    "required": ["param"]
  }
}
```

## Troubleshooting

### Tool Not Appearing in App
- Verify `@IntoSet` annotation in Hilt module
- Check module is included in `settings.gradle.kts`
- Ensure Hilt module is `@InstallIn(SingletonComponent::class)`
- Rebuild: `./gradlew clean build`

### MCP Tool Not Registered
- Verify `configure()` is called in `McpTool` implementation
- Check `server.addTool()` is invoked with correct parameters
- Review logcat for registration errors
- Verify tool is enabled in app UI

### Permission Errors
- Declare permissions in module's `AndroidManifest.xml`
- Return correct permissions from `requiredPermissions()`
- Check Android OS permission is granted before tool execution
- Handle permission denial gracefully

### Server Connection Issues
- Ensure device and client are on same network
- Verify bearer token in Authorization header
- Check device IP address and port are correct
- Review server logs in app UI

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

### Additional Resources

- [MCP Protocol Documentation](https://modelcontextprotocol.io/)
- [Android Developer Guide](https://developer.android.com/)
- [Ktor Documentation](https://ktor.io/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

**Made with ❤️ by Stefan and contributors**
