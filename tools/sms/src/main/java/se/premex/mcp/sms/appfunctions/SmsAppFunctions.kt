package se.premex.mcp.sms.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.service.AppFunction
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.premex.mcp.sms.SmsSender
import javax.inject.Inject

/** Phone actions that send SMS messages directly through the mobile carrier. */
class SmsAppFunctions @Inject constructor(
    private val smsSender: SmsSender,
) {
    /**
     * Send an SMS message immediately through the device's mobile carrier.
     *
     * This action may incur carrier charges and does not show a confirmation UI.
     *
     * @param appFunctionContext The Android execution context used to verify SMS permission.
     * @param phoneNumber Destination phone number, preferably in E.164 format such as +46701234567.
     * @param message Non-empty message body to send.
     * @return A short confirmation that the message was handed to Android's SMS service.
     * @throws AppFunctionPermissionRequiredException If SMS permission has not been granted.
     * @throws AppFunctionInvalidArgumentException If a required value is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendSms(
        appFunctionContext: AppFunctionContext,
        phoneNumber: String,
        message: String,
    ): String {
        if (ContextCompat.checkSelfPermission(
                appFunctionContext.context,
                Manifest.permission.SEND_SMS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw AppFunctionPermissionRequiredException(
                "SMS permission is required. Ask the user to enable the Send SMS tool and grant permission.",
            )
        }
        return executeSendSms(phoneNumber, message)
    }

    internal suspend fun executeSendSms(phoneNumber: String, message: String): String {
        if (phoneNumber.isBlank()) {
            throw AppFunctionInvalidArgumentException("phoneNumber must not be blank.")
        }
        if (message.isBlank()) {
            throw AppFunctionInvalidArgumentException("message must not be blank.")
        }

        val sent = withContext(Dispatchers.IO) {
            smsSender.sendSms(phoneNumber, message)
        }
        if (!sent) {
            throw AppFunctionAppUnknownException(
                "Android could not send the SMS. Ask the user to check mobile service and retry.",
            )
        }
        return "SMS sent to $phoneNumber."
    }
}
