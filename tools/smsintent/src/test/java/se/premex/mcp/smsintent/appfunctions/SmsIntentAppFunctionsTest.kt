package se.premex.mcp.smsintent.appfunctions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.premex.mcp.smsintent.SmsIntentSender

class SmsIntentAppFunctionsTest {
    @Test
    fun executePrepareSms_delegatesToIntentSender() {
        var capturedPhoneNumber = ""
        var capturedMessage = ""
        val sender = object : SmsIntentSender {
            override fun sendSmsIntent(phoneNumber: String, message: String): Boolean {
                capturedPhoneNumber = phoneNumber
                capturedMessage = message
                return true
            }
        }

        val result = SmsIntentAppFunctions(sender)
            .executePrepareSms("+46701234567", "Hello")

        assertEquals("+46701234567", capturedPhoneNumber)
        assertEquals("Hello", capturedMessage)
        assertTrue(result.contains("user review"))
    }
}
