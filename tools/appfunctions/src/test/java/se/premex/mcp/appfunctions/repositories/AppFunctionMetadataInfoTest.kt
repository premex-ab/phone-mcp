package se.premex.mcp.appfunctions.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFunctionMetadataInfoTest {

    @Test
    fun `short name returns simple sanitized form`() {
        val result = AppFunctionMetadataInfo.mcpToolNameFor("com.example", "doSomething")
        assertEquals("appfn__com_example__doSomething", result)
        assertTrue("Name should not exceed 64 chars", result.length <= 64)
    }

    @Test
    fun `name with exactly 64 chars is returned as-is`() {
        // Build a package+function combo whose sanitized form is exactly 64 chars.
        // "appfn__" = 7, "__" between pkg and fn = 2, total prefix = 9
        // We need pkg + fn = 55 chars total (55 + 9 = 64).
        val pkg = "com.example.ab"   // 14 chars -> sanitized "com_example_ab" = 14
        val fn  = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" // 43 chars
        // 7 + 14 + 2 + 43 = 66 — let's compute precisely
        val candidate = "appfn__${pkg}__${fn}".replace(Regex("[^A-Za-z0-9_]"), "_")
        // Adjust the function name so the candidate is exactly 64 chars
        val needed = 64 - 7 - "com_example_ab".length - 2 // = 64 - 7 - 14 - 2 = 41
        val fn64 = "a".repeat(needed)
        val result = AppFunctionMetadataInfo.mcpToolNameFor(pkg, fn64)
        assertEquals(64, result.length)
        // No hash appended — the raw sanitized form should be returned unchanged
        val expected = "appfn__${pkg}__${fn64}".replace(Regex("[^A-Za-z0-9_]"), "_")
        assertEquals(expected, result)
    }

    @Test
    fun `long name is capped to exactly 64 chars with hash suffix`() {
        val pkg = "com.example.someverylongpackagename"
        val fn  = "doSomethingComplexWithParameters"
        val result = AppFunctionMetadataInfo.mcpToolNameFor(pkg, fn)
        assertEquals(
            "Capped name must be exactly 64 chars (55 prefix + '_' + 8-char hex)",
            64,
            result.length,
        )
        // Last 9 chars should be "_" followed by 8 hex digits
        val suffix = result.takeLast(9)
        assertTrue(
            "Suffix should be underscore + 8 hex digits, was: $suffix",
            suffix.matches(Regex("_[0-9a-f]{8}")),
        )
    }

    @Test
    fun `two different long names produce different hash suffixes`() {
        val result1 = AppFunctionMetadataInfo.mcpToolNameFor(
            "com.example.someverylongpackagename",
            "doSomethingComplexWithParameters",
        )
        val result2 = AppFunctionMetadataInfo.mcpToolNameFor(
            "com.example.someverylongpackagename",
            "doSomethingComplexWithOtherParameters",
        )
        // Both should be 64 chars
        assertEquals(64, result1.length)
        assertEquals(64, result2.length)
        // The 8-char hex suffixes must differ (collision would be extremely unlikely)
        val hash1 = result1.takeLast(8)
        val hash2 = result2.takeLast(8)
        assertNotEquals("Different inputs must produce different hashes", hash1, hash2)
    }

    @Test
    fun `special characters in package and function are replaced with underscores`() {
        val result = AppFunctionMetadataInfo.mcpToolNameFor("com.my-app.test", "my-function/v2")
        assertTrue("Result should only contain alphanumeric and underscores", result.matches(Regex("[A-Za-z0-9_]+")))
    }
}
