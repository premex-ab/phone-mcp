<!--
Sync Impact Report - Version 1.0.0 (Initial Constitution)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Version Change: NONE → 1.0.0 (Initial ratification)

Established Principles:
  • I. Modular Tool Architecture
  • II. Privacy & Security First
  • III. McpTool Contract Compliance
  • IV. Standardized Module Structure
  • V. Dependency Injection & Lifecycle
  • VI. Clean Architecture & Separation of Concerns

Sections Added:
  • Core Principles (6 principles)
  • Security & Privacy Requirements
  • Tool Module Development Standards
  • Governance

Generated: 2025-12-28
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-->

# Android MCP Server Constitution

## Core Principles

### I. Modular Tool Architecture

Every device capability MUST be implemented as a separate, pluggable tool module. Each module MUST:
- Reside in `tools/{lowercase_tool_name}/` with standardized directory structure
- Implement the `McpTool` interface from the `core` module
- Be independently buildable and testable without dependencies on other tool modules
- Register itself via Hilt's `@IntoSet` annotation to the application's `Set<McpTool>`
- Use the `mcp.android.tool` convention plugin for consistent build configuration

**Rationale**: Modularity enables independent development, testing, and maintenance of device capabilities. Tools can be enabled/disabled at runtime without affecting others. Third-party developers can create external tool modules following the same contract.

### II. Privacy & Security First (NON-NEGOTIABLE)

Every tool that accesses sensitive device capabilities or user data MUST implement explicit consent and security measures:
- **Permission Declaration**: All required Android permissions MUST be declared in `AndroidManifest.xml` and returned by `requiredPermissions()`
- **Default Disabled**: Tools accessing sensitive data MUST set `enabledByDefault = false`
- **Disclaimer Required**: Tools MUST provide a comprehensive `disclaim` string explaining:
  - What data is accessed
  - How the AI assistant will use the data
  - User responsibilities and compliance requirements
  - Potential privacy implications
- **Runtime Consent**: Users MUST explicitly enable each tool through the UI after reviewing disclaimers
- **Bearer Token Auth**: All MCP server connections MUST be authenticated with bearer tokens
- **Foreground Service**: The MCP server MUST run as a foreground service with persistent notification

**Rationale**: Users must have informed consent and control over what device capabilities are exposed to AI assistants. Privacy violations or unauthorized access can have serious consequences for users and regulatory compliance.

### III. McpTool Contract Compliance

All tool modules MUST strictly implement the `McpTool` interface contract without deviation:
- `id: String` - Unique identifier (lowercase, no spaces)
- `name: String` - Human-readable display name
- `enabledByDefault: Boolean` - Default enabled state (false for sensitive tools)
- `disclaim: String?` - Disclaimer text (required for sensitive tools, null otherwise)
- `configure(server: Server)` - Register MCP tools with the server
- `requiredPermissions(): Set<String>` - Return all required Android permissions

MCP tool names MUST follow the convention: `phone_{action}` (snake_case with "phone_" prefix).

**Rationale**: A consistent contract enables the application to discover, manage, and integrate tools dynamically without coupling to specific implementations. This contract is the foundation of the entire modular architecture.

### IV. Standardized Module Structure

Every tool module MUST follow the established directory structure and file organization pattern:

```
tools/{lowercase_tool_name}/
├── build.gradle.kts                                    # Convention plugin configuration
├── src/main/
    ├── AndroidManifest.xml                             # Permission declarations
    └── java/se/premex/mcp/{lowercase_tool_name}/
        ├── configurator/
        │   ├── {Toolname}ToolConfigurator.kt           # Interface
        │   └── {Toolname}ToolConfiguratorImpl.kt       # Implementation
        ├── di/
        │   └── {Toolname}ToolModule.kt                 # Hilt module with @IntoSet
        ├── repositories/
        │   ├── {Toolname}Info.kt                       # Data classes
        │   ├── {Toolname}Repository.kt                 # Interface
        │   └── {Toolname}RepositoryImpl.kt             # Implementation
        └── tool/
            └── {Toolname}Tool.kt                       # McpTool implementation
```

**Naming conventions**:
- Module names: lowercase (e.g., `tools/camera`)
- Package names: `se.premex.mcp.{toolname}`
- Class names: PascalCase (e.g., `CameraTool`)
- File names: Match class names exactly

**Rationale**: Standardized structure reduces cognitive load, enables developers to navigate any tool module quickly, and supports automated tooling and code generation. Consistency is essential for maintaining a multi-module project.

### V. Dependency Injection & Lifecycle Management

All dependencies MUST be managed through Hilt (Dagger) with proper lifecycle scoping:
- **Repositories**: MUST be `@Singleton` and provided in tool modules
- **Tools**: MUST be provided via `@IntoSet` to contribute to `Set<McpTool>`
- **Context**: MUST use `@ApplicationContext` annotation when injecting `Context`
- **InstallIn**: Tool modules MUST use `@InstallIn(SingletonComponent::class)`
- **Coroutines**: MUST use `suspend` functions for async operations, prefer `Dispatchers.IO` for I/O operations
- **Service Lifecycle**: MCP server MUST run as foreground service, tools MUST be lifecycle-aware

**Rationale**: Proper dependency injection eliminates tight coupling, enables testability through mocking, and ensures correct lifecycle management. Hilt provides compile-time validation and integrates seamlessly with Android components.

### VI. Clean Architecture & Separation of Concerns

The codebase MUST maintain clear architectural boundaries and separation of concerns:
- **Core Module**: Contains ONLY the `McpTool` interface (Java library, not Android library)
- **App Module**: Contains UI (Compose), server logic (`McpServerService`), tool management, and configuration
- **Tool Modules**: Contain ONLY tool-specific implementation (repository, configurator, McpTool impl)
- **MCP Provider**: SDK library for external tool integration (Content Provider based)
- **Repository Pattern**: MUST be used for all Android API interactions (interface + implementation)
- **Error Handling**: Tools MUST return `CallToolResult` with error messages, MUST NOT throw exceptions to MCP layer
- **Logging**: MUST use the project's custom logging interface (wraps logcat) with consistent tags for debugging. App uses Firebase logging and Crashlytics for error tracking

**Architecture pattern**:
- All tool modules MUST use the Configurator-Based pattern: separate configurator interface/impl, repository interface/impl, and tool class that implements `McpTool`

**Rationale**: Clean architecture ensures maintainability, testability, and long-term evolution of the codebase. Clear boundaries prevent tool modules from becoming tightly coupled to the application or each other. The repository pattern abstracts Android APIs, enabling unit testing and platform independence.

## Security & Privacy Requirements

### Authentication & Network Security

- **Bearer Token Generation**: MUST use cryptographically secure random token generation
- **Token Storage**: MUST persist tokens using DataStore with appropriate encryption
- **HTTPS Recommended**: While the server supports HTTP for local connections, production deployments SHOULD use HTTPS with valid certificates
- **CORS Configuration**: MUST be explicitly configured to prevent unauthorized cross-origin access
- **Network Permissions**: MUST declare `INTERNET` permission in app manifest

### Permission Management

- **Runtime Permissions**: MUST request dangerous permissions at runtime (API 23+)
- **Permission Rationale**: MUST provide clear explanation before requesting permissions
- **Graceful Degradation**: Tools MUST handle permission denial gracefully without crashing
- **Permission Status**: App MUST display current permission status for each tool in UI
- **Revocation Handling**: Tools MUST detect and handle permission revocations

### Data Privacy

- **No Persistent Storage**: Tool implementations SHOULD NOT persist user data unless explicitly required for functionality
- **Data Minimization**: MUST only access/transmit data necessary for the requested MCP tool operation
- **Error Logging**: App uses Firebase logging and Crashlytics for error tracking (MUST NOT log sensitive user data)
- **Foreground Disclosure**: Server MUST maintain visible foreground notification whenever running

## Tool Module Development Standards

### Build Configuration

All tool modules MUST use the convention plugin:

```kotlin
plugins {
    alias(libs.plugins.mcp.android.tool)
}

android {
    namespace = "se.premex.mcp.{tool_name}"
}

dependencies {
    // Additional dependencies beyond convention plugin defaults
    // Example: implementation(libs.androidx.lifecycle.process)
}
```

The convention plugin automatically provides: compileSdk=36, minSdk=24, Kotlin JVM 21, Hilt, MCP SDK, Ktor, standard Android libraries.

### MCP Tool Registration

Tools MUST register with the MCP server following this pattern:

```kotlin
server.addTool(
    name = "phone_{action}",  // MUST prefix with "phone_"
    description = """
        Detailed description with parameters, behavior, and limitations.
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
    // Validate parameters
    val param = request.arguments["param_name"]?.jsonPrimitive?.content
        ?: return@addTool CallToolResult(
            content = listOf(TextContent("Error: param_name is required"))
        )
    
    // Execute operation
    val result = repository.doOperation(param)
    
    // Return result
    CallToolResult(content = listOf(TextContent(result)))
}
```

### Testing Requirements

- **Repository Testing**: Repository implementations SHOULD have unit tests
- **Integration Testing**: Tool modules SHOULD have integration tests validating MCP tool contracts
- **Permission Testing**: MUST test behavior with permissions granted and denied
- **Error Handling**: MUST test error scenarios and validate proper `CallToolResult` error messages

### Documentation Requirements

Each tool module MUST provide:
- Clear KDoc comments on public APIs
- README if the tool has external dependencies or complex setup
- Disclaimer text explaining privacy implications
- Parameter descriptions in MCP tool registration

### Code Style

- **Indentation**: 4 spaces (consistent across project)
- **Null Safety**: Prefer null-safe operators (`?.`, `?:`)
- **Coroutines**: Use `suspend` functions for async operations
- **Error Handling**: Return errors as `CallToolResult`, don't throw
- **Logging**: Use project's custom logging interface (to be implemented, wraps logcat) with consistent tags. Firebase logging and Crashlytics used for error tracking

### External Tool Support

Third-party apps can integrate by:
1. Depending on `mcp-provider` module
2. Extending `McpProvider` class
3. Implementing `getToolInfo()` and `executeToolRequest()`
4. Registering provider in `AndroidManifest.xml` with authority: `${applicationId}.authorities.McpProvider`

The `externaltools` module automatically discovers and integrates Content Provider-based tools.

## Governance

This constitution supersedes all other project practices and guidelines. All development activities MUST comply with these principles.

### Amendment Process

1. **Proposal**: Amendments MUST be proposed via pull request with rationale
2. **Impact Analysis**: MUST document which principles/sections are affected and migration implications
3. **Version Increment**: 
   - **MAJOR**: Backwards-incompatible governance changes, principle removals, or redefinitions
   - **MINOR**: New principles/sections added or materially expanded guidance
   - **PATCH**: Clarifications, wording improvements, typo fixes
4. **Review**: Amendments require review and approval from project maintainers
5. **Migration**: If amendment affects existing code, MUST include migration plan

### Compliance & Enforcement

- All pull requests MUST be reviewed for constitutional compliance
- New tool modules MUST follow the standardized structure (use `/tools/CREATE_MODULE_INSTRUCTIONS.md`)
- Privacy-sensitive changes MUST undergo additional security review
- Complexity that violates principles MUST be justified in implementation plans

### Living Documentation

- Primary guidance: `.github/copilot-instructions.md` (this document)
- Tool creation: `/tools/CREATE_MODULE_INSTRUCTIONS.md`
- Specification workflow: `.specify/templates/*.md`
- Constitution amendments: This file

**Version**: 1.0.0 | **Ratified**: 2025-12-28 | **Last Amended**: 2025-12-28
