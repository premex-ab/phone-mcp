package se.premex.mcp.contacts.appfunctions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import se.premex.mcp.contacts.repositories.ContactPhoneInfo
import se.premex.mcp.contacts.repositories.ContactsRepository

class ContactsAppFunctionsTest {
    @Test
    fun executeSearchContacts_mapsRepositoryResults() = runBlocking {
        var capturedName = ""
        val repository = object : ContactsRepository {
            override fun findPhoneNumberByName(name: String): List<ContactPhoneInfo> {
                capturedName = name
                return listOf(ContactPhoneInfo("Ada Lovelace", "+441234", "Mobile"))
            }
        }

        val result = ContactsAppFunctions(repository).executeSearchContacts("Ada")

        assertEquals("Ada", capturedName)
        assertEquals(
            listOf(AppFunctionContact("Ada Lovelace", "+441234", "Mobile")),
            result,
        )
    }
}
