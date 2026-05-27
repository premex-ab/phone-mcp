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

        val props = schema.properties!!
        assertTrue(props.containsKey("recipient"))
        val recipient = props["recipient"] as JsonObject
        assertEquals(JsonPrimitive("string"), recipient["type"])
        assertEquals(JsonPrimitive("Phone number to send to."), recipient["description"])
        assertEquals(listOf("recipient"), schema.required!!)
    }

    @Test
    fun `omits optional parameters from required list but keeps them in properties`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("a", AppFunctionParameterSpec.ParameterType.STRING, "first", required = true),
                AppFunctionParameterSpec("b", AppFunctionParameterSpec.ParameterType.STRING, "second", required = false),
            )
        )

        assertEquals(listOf("a"), schema.required!!)
        assertTrue(schema.properties!!.containsKey("a"))
        assertTrue(schema.properties!!.containsKey("b"))
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

        assertEquals(JsonPrimitive("string"),  (schema.properties!!["s"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("integer"), (schema.properties!!["i"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("integer"), (schema.properties!!["l"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("boolean"), (schema.properties!!["b"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("number"),  (schema.properties!!["n"] as JsonObject)["type"])
    }

    @Test
    fun `maps STRING_ARRAY to array with string items`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("tags", AppFunctionParameterSpec.ParameterType.STRING_ARRAY, "Tag list", required = false),
            )
        )

        val tags = schema.properties!!["tags"] as JsonObject
        assertEquals(JsonPrimitive("array"), tags["type"])
        val items = tags["items"] as JsonObject
        assertEquals(JsonPrimitive("string"), items["type"])
    }

    @Test
    fun `handles empty parameter list`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(emptyList())
        assertTrue(schema.properties!!.isEmpty())
        assertTrue(schema.required!!.isEmpty())
    }
}
