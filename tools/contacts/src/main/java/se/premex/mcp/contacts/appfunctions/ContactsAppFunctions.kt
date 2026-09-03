package se.premex.mcp.contacts.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.premex.mcp.contacts.repositories.ContactsRepository
import javax.inject.Inject

/** A contact and phone number returned from the device address book. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFunctionContact(
    /** Display name stored in the address book. */
    val name: String,
    /** Phone number exactly as stored in the address book. */
    val phoneNumber: String,
    /** Contact phone-number category, such as Mobile, Home, Work, or Other. */
    val phoneType: String,
)

/** Phone actions that search the user's address book. */
class ContactsAppFunctions @Inject constructor(
    private val contactsRepository: ContactsRepository,
) {
    /**
     * Search the device address book for phone numbers matching a contact name.
     *
     * @param appFunctionContext The Android execution context used to verify contacts permission.
     * @param name Full or partial contact name. Matching is case-insensitive on standard Android providers.
     * @return Every matching contact phone number, ordered by display name.
     * @throws AppFunctionPermissionRequiredException If contacts permission has not been granted.
     * @throws AppFunctionInvalidArgumentException If the search name is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchContacts(
        appFunctionContext: AppFunctionContext,
        name: String,
    ): List<AppFunctionContact> {
        if (ContextCompat.checkSelfPermission(
                appFunctionContext.context,
                Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw AppFunctionPermissionRequiredException(
                "Contacts permission is required. Ask the user to enable Contacts and grant permission.",
            )
        }
        return executeSearchContacts(name)
    }

    internal suspend fun executeSearchContacts(name: String): List<AppFunctionContact> {
        if (name.isBlank()) {
            throw AppFunctionInvalidArgumentException("name must not be blank.")
        }
        return withContext(Dispatchers.IO) {
            contactsRepository.findPhoneNumberByName(name).map { contact ->
                AppFunctionContact(
                    name = contact.contactName,
                    phoneNumber = contact.phoneNumber,
                    phoneType = contact.phoneType,
                )
            }
        }
    }
}
