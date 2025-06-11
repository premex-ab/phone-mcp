package se.premex.mcp.contacts.repositories

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

class ContactsRepositoryImpl(
    private val context: Context
): ContactsRepository {
    /**
     * Find a contact's phone number by name
     * @param name The name to search for (can be partial)
     * @return A list of phone numbers with contact names
     */
    override fun findPhoneNumberByName(name: String): List<ContactPhoneInfo> {
        val result = mutableListOf<ContactPhoneInfo>()
        val contentResolver: ContentResolver = context.contentResolver

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        // Name search selection
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.let {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext()) {
                    val contactName = it.getString(nameIndex)
                    val phoneNumber = it.getString(numberIndex)
                    val phoneType = it.getInt(typeIndex)

                    val phoneTypeString = getPhoneTypeString(phoneType)
                    result.add(ContactPhoneInfo(contactName, phoneNumber, phoneTypeString))
                }
            }
        } finally {
            cursor?.close()
        }

        return result
    }

    private fun getPhoneTypeString(phoneType: Int): String {
        return when (phoneType) {
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            else -> "Other"
        }
    }
}

data class ContactPhoneInfo(
    val contactName: String,
    val phoneNumber: String,
    val phoneType: String
)
