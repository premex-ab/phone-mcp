package se.premex.mcp.smsintent

interface SmsIntentSender {
    fun sendSmsIntent(phoneNumber: String, message: String): Boolean
}