package se.premex.mcpserver.auth

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that handles authentication for the MCP server.
 * Currently using a fake implementation that always succeeds and returns a random user ID.
 */
@Singleton
class AuthRepository @Inject constructor() {

    /**
     * Validates a bearer token and returns a user ID if valid.
     * In this implementation, we're always returning success with a random user ID.
     *
     * @param token The bearer token to validate
     * @return The user ID if authentication is successful, null otherwise
     */
    fun validateBearerToken(token: String?): String? {
        // For now, we're just returning a successful authentication with a random user ID
        // In a real implementation, you would validate the token against a database or service
        return if (token != null) {
            // Generate a random UUID for the user ID
            UUID.randomUUID().toString()
        } else {
            null
        }
    }
}
