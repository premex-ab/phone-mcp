package se.premex.mcp.contacts.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.contacts.repositories.ContactsRepository
import se.premex.mcp.contacts.serverconfigurator.appendContactsTools
import se.premex.mcp.core.tool.McpTool

class ContactsTool(val contactsRepository: ContactsRepository) : McpTool {
    override val id: String = "contacts"
    override val name: String = "Read contacts"
    override val enabledByDefault: Boolean = false
    override val disclaim: String?
        get() = "PRIVACY WARNING: Enabling contacts access\n\n" +
                "By enabling this tool, you grant this application and any connected AI services permission to:\n" +
                "• Read your contact list including names, phone numbers, and other personal information\n" +
                "• Process this personal identifiable information (PII) for responding to your requests\n\n" +
                "You acknowledge that:\n" +
                "• You have obtained necessary consent from individuals in your contacts list\n" +
                "• You are responsible for ensuring any AI services you connect to comply with applicable privacy regulations\n" +
                "• You can revoke access at any time by disabling this tool\n\n" +
                "We do not store your contacts data, but connected AI services may process this information according to their privacy policies."

    override fun configure(server: Server) {
        appendContactsTools(server, contactsRepository)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf(android.Manifest.permission.READ_CONTACTS)
    }
}