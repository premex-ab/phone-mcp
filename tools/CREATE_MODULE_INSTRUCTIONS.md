# Creating a New MCP Tool Module

This document provides instructions for creating a new tool module for the Phone MCP application.

## Module Structure

Each tool module should follow this standard directory structure:

```
tools/{LOWERCASE_TOOL_NAME}/
├── build.gradle.kts
├── src/
    └── main/
        ├── AndroidManifest.xml
        └── java/
            └── se/
                └── premex/
                    └── mcp/
                        └── {LOWERCASE_TOOL_NAME}/
                            ├── configurator/
                            │   ├── {Toolname}ToolConfigurator.kt
                            │   └── {Toolname}ToolConfiguratorImpl.kt
                            ├── di/
                            │   └── {Toolname}Tool.kt
                            ├── repositories/
                            │   ├── {Toolname}Info.kt
                            │   ├── {Toolname}Repository.kt
                            │   └── {Toolname}RepositoryImpl.kt
                            └── tool/
                                └── {Toolname}Tool.kt
```

## Step-by-Step Instructions

### 1. Create Module Directory

Create a new directory for your tool under the `tools/` folder. Use lowercase for the directory name:

```
tools/{your_tool_name}/
```

### 2. Create build.gradle.kts

Create a `build.gradle.kts` file in your module directory with the following content:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "se.premex.mcp.{your_tool_name}"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Module dependencies
    implementation(project(":core"))

    api(libs.io.modelcontextprotocol.kotlin.sdk)
    implementation(libs.io.ktor.ktor.client.core)
    implementation(libs.io.ktor.ktor.client.cio)
    implementation(libs.io.ktor.ktor.client.content.negotiation)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### 3. Create the Directory Structure

Create all required directories following the structure outlined above:

```
mkdir -p src/main/java/se/premex/mcp/{your_tool_name}/{configurator,di,repositories,tool}
```

### 4. Create AndroidManifest.xml

Create an `AndroidManifest.xml` file in the `src/main/` directory:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Add any required permissions for your tool -->
    <!-- <uses-permission android:name="android.permission.YOUR_PERMISSION_HERE"/> -->
</manifest>
```

### 5. Create the Required Files

#### ToolConfigurator Interface

Create `{Toolname}ToolConfigurator.kt` in the `configurator` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface {Toolname}ToolConfigurator {
    fun configure(server: Server)
}
```

#### ToolConfigurator Implementation

Create `{Toolname}ToolConfiguratorImpl.kt` in the `configurator` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.configurator

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.{your_tool_name}.repositories.{Toolname}Repository

class {Toolname}ToolConfiguratorImpl(
    private val {toolname}Repository: {Toolname}Repository,
) : {Toolname}ToolConfigurator {

    /**
     * Configures the MCP server with the {your_tool_name} tool.
     */
    override fun configure(server: Server) {
        server.addTool(
            name = "phone_{your_tool_name}",
            description = """
                Description of your tool functionality.
                Provide detailed information about what this tool does
                and what capabilities it provides.
            """.trimIndent(),
        ) { request ->
            try {
                val result = {toolname}Repository.getData()
                
                if (result.isEmpty()) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("No data found."))
                    )
                }

                CallToolResult(
                    content = result.map { item ->
                        TextContent(item.toString())
                    }
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Error: ${e.message}"))
                )
            }
        }
    }
}
```

#### Info Data Class

Create `{Toolname}Info.kt` in the `repositories` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.repositories

data class {Toolname}Info(
    // Add relevant properties for your tool
    val id: String,
    val name: String,
    val description: String
) {
    override fun toString(): String {
        return """
            ID: $id
            Name: $name
            Description: $description
        """.trimIndent()
    }
}
```

#### Repository Interface

Create `{Toolname}Repository.kt` in the `repositories` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.repositories

interface {Toolname}Repository {
    fun getData(): List<{Toolname}Info>
    // Add other methods as needed
}
```

#### Repository Implementation

Create `{Toolname}RepositoryImpl.kt` in the `repositories` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.repositories

import android.content.Context

class {Toolname}RepositoryImpl(
    private val context: Context
) : {Toolname}Repository {
    
    override fun getData(): List<{Toolname}Info> {
        // Implement the logic to gather the required data
        // Use the Android context as needed
        
        return try {
            // Your implementation here
            listOf(
                {Toolname}Info(
                    id = "example-id",
                    name = "Example Name",
                    description = "Example Description"
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

#### Tool Class

Create `{Toolname}Tool.kt` in the `tool` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.{your_tool_name}.configurator.{Toolname}ToolConfiguratorImpl

class {Toolname}Tool(
    val {toolname}ToolConfigurator: {Toolname}ToolConfiguratorImpl
) : McpTool {
    override val id: String = "{your_tool_name}"
    override val name: String = "{Toolname}"
    override val enabledByDefault: Boolean = true
    override val disclaim: String? = null

    override fun configure(server: Server) {
        {toolname}ToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        // Add any required runtime permissions
        return setOf(
            // Example: android.Manifest.permission.CAMERA
        )
    }
}
```

#### DI Module

Create `{Toolname}Tool.kt` in the `di` directory:

```kotlin
package se.premex.mcp.{your_tool_name}.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.{your_tool_name}.repositories.{Toolname}Repository
import se.premex.mcp.{your_tool_name}.repositories.{Toolname}RepositoryImpl
import se.premex.mcp.{your_tool_name}.configurator.{Toolname}ToolConfiguratorImpl
import se.premex.mcp.{your_tool_name}.tool.{Toolname}Tool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object {Toolname}ToolModule {

    @Provides
    @Singleton
    @IntoSet
    fun provide{Toolname}Tool(@ApplicationContext context: Context): McpTool {
        val {toolname}Repository: {Toolname}Repository = {Toolname}RepositoryImpl(context)
        val {toolname}ToolConfigurator = {Toolname}ToolConfiguratorImpl({toolname}Repository)
        return {Toolname}Tool({toolname}ToolConfigurator)
    }
}
```

### 6. Update settings.gradle.kts

**IMPORTANT**: You must add your new module to the settings.gradle.kts file at the project root to include it in the build:

```kotlin
// Add this line to settings.gradle.kts
include(":tools:{your_tool_name}")
```

For example, if you created a camera module, you would add:

```kotlin
include(":tools:camera")
```

Without this step, Gradle will not recognize your module and it will not be built or included in the application.

### 7. Add Your Module to the App

Update the app's build.gradle.kts file to include your new module:

```kotlin
dependencies {
    // Other dependencies...
    implementation(project(":tools:{your_tool_name}"))
}
```

## Best Practices

1. **Naming Conventions**:
   - Use lowercase for directory names
   - Use PascalCase for class names
   - Use camelCase for variable and method names

2. **Permissions**:
   - Include only the necessary permissions in your AndroidManifest.xml
   - Request runtime permissions in the requiredPermissions method of your Tool class

3. **Error Handling**:
   - Always include try-catch blocks when interacting with device hardware
   - Provide meaningful error messages to help with debugging

4. **Documentation**:
   - Include descriptive comments for methods and classes
   - Document the purpose of your tool and how it works

5. **Testing**:
   - Add unit tests for your repository implementation
   - Test your tool with various edge cases

## Example

For a tool named "location", the structure would be:

- Directory: `tools/location/`
- Files:
  - `LocationToolConfigurator.kt`
  - `LocationToolConfiguratorImpl.kt`
  - `LocationTool.kt` (in di/ folder)
  - `LocationInfo.kt`
  - `LocationRepository.kt`
  - `LocationRepositoryImpl.kt`
  - `LocationTool.kt` (in tool/ folder)

With these files in place, your new MCP tool module will be ready for integration with the main application.
