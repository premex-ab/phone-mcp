package se.premex.mcp.core.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        assertNotNull("schema.properties should not be null", schema.properties)
        val props = schema.properties!!
        assertTrue(props.containsKey("recipient"))
        val recipient = props["recipient"] as JsonObject
        assertEquals(JsonPrimitive("string"), recipient["type"])
        assertEquals(JsonPrimitive("Phone number to send to."), recipient["description"])
        assertNotNull("schema.required should not be null", schema.required)
        val required = schema.required!!
        assertEquals(listOf("recipient"), required)
    }

    @Test
    fun `omits optional parameters from required list but keeps them in properties`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("a", AppFunctionParameterSpec.ParameterType.STRING, "first", required = true),
                AppFunctionParameterSpec("b", AppFunctionParameterSpec.ParameterType.STRING, "second", required = false),
            )
        )

        assertNotNull("schema.required should not be null", schema.required)
        assertNotNull("schema.properties should not be null", schema.properties)
        val required = schema.required!!
        val props = schema.properties!!
        assertEquals(listOf("a"), required)
        assertTrue(props.containsKey("a"))
        assertTrue(props.containsKey("b"))
    }

    @Test
    fun `maps each supported primitive type to the correct JSON schema type`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("s", AppFunctionParameterSpec.ParameterType.STRING, null, required = true),
                AppFunctionParameterSpec("i", AppFunctionParameterSpec.ParameterType.INTEGER, null, required = true),
                AppFunctionParameterSpec("l", AppFunctionParameterSpec.ParameterType.LONG, null, required = true),
                AppFunctionParameterSpec("b", AppFunctionParameterSpec.ParameterType.BOOLEAN, null, required = true),
                AppFunctionParameterSpec("n", AppFunctionParameterSpec.ParameterType.NUMBER, null, required = true),
            )
        )

        assertNotNull("schema.properties should not be null", schema.properties)
        val props = schema.properties!!
        assertEquals(JsonPrimitive("string"),  (props["s"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("integer"), (props["i"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("integer"), (props["l"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("boolean"), (props["b"] as JsonObject)["type"])
        assertEquals(JsonPrimitive("number"),  (props["n"] as JsonObject)["type"])
    }

    @Test
    fun `maps STRING_ARRAY to array with string items`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec("tags", AppFunctionParameterSpec.ParameterType.STRING_ARRAY, "Tag list", required = false),
            )
        )

        assertNotNull("schema.properties should not be null", schema.properties)
        val props = schema.properties!!
        val tags = props["tags"] as JsonObject
        assertEquals(JsonPrimitive("array"), tags["type"])
        val items = tags["items"] as JsonObject
        assertEquals(JsonPrimitive("string"), items["type"])
    }

    @Test
    fun `handles empty parameter list`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(emptyList())
        assertNotNull("schema.properties should not be null", schema.properties)
        assertNotNull("schema.required should not be null", schema.required)
        val props = schema.properties!!
        val required = schema.required!!
        assertTrue(props.isEmpty())
        assertTrue(required.isEmpty())
    }

    @Test
    fun `omits description JSON key when description is null`() {
        val schema = AppFunctionSchemaMapper.toMcpToolSchema(
            listOf(
                AppFunctionParameterSpec(
                    name = "x",
                    type = AppFunctionParameterSpec.ParameterType.STRING,
                    description = null,
                    required = true,
                )
            )
        )
        assertNotNull("schema.properties should not be null", schema.properties)
        val props = schema.properties!!
        val x = props["x"] as JsonObject
        assertFalse("description key should be absent when null", x.containsKey("description"))
    }
}
