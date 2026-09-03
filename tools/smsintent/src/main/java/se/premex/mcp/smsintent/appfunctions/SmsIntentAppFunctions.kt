package se.premex.mcp.smsintent.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.service.AppFunction
import androidx.core.content.ContextCompat
import se.premex.mcp.smsintent.SmsIntentSender
import javax.inject.Inject

/** Phone actions that prepare SMS messages for user review. */
class SmsIntentAppFunctions @Inject constructor(
    private val smsIntentSender: SmsIntentSender,
) {
    /**
     * Prepare an SMS message for the user to review and send.
     *
     * This function never sends the message automatically. It opens the SMS app when
     * possible, or posts a notification that the user can tap to continue.
     *
     * @param appFunctionContext The Android execution context used to verify notification permission.
     * @param phoneNumber Destination phone number, preferably in E.164 format such as +46701234567.
     * @param message Non-empty message body to place in the SMS composer.
     * @return A short confirmation that the message was prepared for user review.
     * @throws AppFunctionPermissionRequiredException If notifications are required but unavailable.
     * @throws AppFunctionInvalidArgumentException If a required value is blank.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun prepareSms(
        appFunctionContext: AppFunctionContext,
        phoneNumber: String,
        message: String,
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appFunctionContext.context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw AppFunctionPermissionRequiredException(
                "Notification permission is required. Ask the user to grant it and retry.",
            )
        }
        return executePrepareSms(phoneNumber, message)
    }

    internal fun executePrepareSms(phoneNumber: String, message: String): String {
        if (phoneNumber.isBlank()) {
            throw AppFunctionInvalidArgumentException("phoneNumber must not be blank.")
        }
        if (message.isBlank()) {
            throw AppFunctionInvalidArgumentException("message must not be blank.")
        }
        if (!smsIntentSender.sendSmsIntent(phoneNumber, message)) {
            throw AppFunctionAppUnknownException(
                "Android could not prepare the SMS. Ask the user to retry from the Phone MCP app.",
            )
        }
        return "SMS prepared for $phoneNumber; user review is required before sending."
    }
}
