package se.premex.mcp.sms

interface SmsSender {
    fun sendSms(phoneNumber: String, message: String): Boolean
}