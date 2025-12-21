package se.premex.mcp.contacts.repositories

interface ContactsRepository {
     fun findPhoneNumberByName(name: String): List<ContactPhoneInfo>
}