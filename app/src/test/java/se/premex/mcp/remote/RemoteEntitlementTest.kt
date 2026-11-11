package se.premex.mcp.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteEntitlementTest {
    @Test fun `grace includes seven extra days and rounds partial days up`() {
        val grace = RemoteEntitlement("grace", "2027-01-02T00:00:00.123Z")
        val parsedEnd = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse("2027-01-02T00:00:00")!!.time
        assertEquals(7, grace.daysLeft(parsedEnd + 1))
        assertEquals(1, grace.daysLeft(parsedEnd + 7 * 86_400_000L - 1))
        assertEquals(0, grace.daysLeft(parsedEnd + 7 * 86_400_000L))
    }
    @Test fun `trial partial day does not say zero and malformed dates fail safely`() {
        assertEquals(1, RemoteEntitlement("trial", "1970-01-02T00:00:00Z").daysLeft(1))
        assertEquals(0, RemoteEntitlement("trial", "invalid").daysLeft(0))
    }
}
