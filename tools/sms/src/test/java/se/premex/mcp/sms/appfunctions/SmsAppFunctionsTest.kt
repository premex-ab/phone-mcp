package se.premex.mcp.sms.appfunctions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.premex.mcp.sms.SmsSender

class SmsAppFunctionsTest {
    @Test
    fun executeSendSms_delegatesToSender() = runBlocking {
        var capturedPhoneNumber = ""
        var capturedMessage = ""
        val sender = object : SmsSender {
            override fun sendSms(phoneNumber: String, message: String): Boolean {
                capturedPhoneNumber = phoneNumber
                capturedMessage = message
                return true
            }
        }

        val result = SmsAppFunctions(sender).executeSendSms("+46701234567", "Hello")

        assertEquals("+46701234567", capturedPhoneNumber)
        assertEquals("Hello", capturedMessage)
        assertTrue(result.contains("+46701234567"))
    }
}
