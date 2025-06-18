package se.premex.mcp.sms

interface SmsIntentSender {
    fun sendSmsIntent(phoneNumber: String, message: String): Boolean
}