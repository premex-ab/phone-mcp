This is a multi module Android app that starts an SSE MCP (Model Context Protocol) server. 
Ais can then connect to the server and send commands to the app. The app will then respond with the results of the commands.

The app contains of 3 core modules:
/app module is the main app module that contains the UI and the logic to start the server.
The /core contains the Interface for each MCP tool that can be configured and used by the MCP server.
Then we have multiple modules in /tools/* that implements McpTool interface and provides the actual functionality for each tool.

the McpTool implementations is provided to the App module through hilt Provides IntoSet

When creating a new tool module this is the recommended structure:

tools/{LOWERCASE_TOOL_NAME}/build.gradle.kts
tools/{LOWERCASE_TOOL_NAME}/src/main/AndroidManifest.xml
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/configurator/{Toolname}ToolConfigurator.kt
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/configurator/{Toolname}ToolConfiguratorImpl.kt
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/di/{Toolname}Tool.kt
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/repositories/{Toolname}Info.kt
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/repositories/{Toolname}Repository.kt
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/repositories/{Toolname}RepositoryImpl.kt
tools/{LOWERCASE_TOOL_NAME}/src/main/java/se/premex/mcp/{LOWERCASE_TOOL_NAME}/tool/{Toolname}Tool.kt
