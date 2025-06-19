package se.premex.mcp.input.configurator

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import se.premex.mcp.input.repositories.InputRepository
import javax.inject.Inject

class InputToolConfiguratorImpl @Inject constructor(
    private val inputRepository: InputRepository
) : InputToolConfigurator {

    override fun configure(server: Server) {
        setupClickFunctionality(server)
        // More input methods can be added here in the future
    }

    private fun setupClickFunctionality(server: Server) {


        server.addTool(
            name = "click_on_phone_screen",
            description = """
                performs a click on the phone screen at the specified coordinates.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("x") {
                        put("type", "int")
                        put(
                            "description",
                            "x coordinate of the click position on the phone screen"
                        )
                    }
                    putJsonObject("y") {
                        put("type", "integer")
                        put(
                            "description",
                            "y coordinate of the click position on the phone screen"
                        )
                    }
                }
            )
        ) { request ->
            val x = request.arguments["x"]?.jsonPrimitive?.content?.toIntOrNull()!!
            val y = request.arguments["y"]?.jsonPrimitive?.content?.toIntOrNull()!!

            val result = inputRepository.performClick(x, y)
            val textResult = if (result) {
                "Successfully clicked at coordinates ($x, $y)"
            } else {
                "Failed to click at coordinates ($x, $y)"
            }

            CallToolResult(
                content = listOf(
                    TextContent(
                        textResult
                    )
                )
            )

        }

    }
}
