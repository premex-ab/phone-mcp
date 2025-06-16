package se.premex.mcp.externaltools.repositories

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ExternalToolRepository that stores tool registration data in SharedPreferences
 */
@Singleton
class ExternalToolRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : ExternalToolRepository {

    companion object {
        private const val PREFS_NAME = "external_tools_prefs"
        private const val KEY_REGISTERED_TOOLS = "registered_tools"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveExternalTool(toolInfo: ExternalToolInfo) {
        val tools = getRegisteredTools().toMutableList()
        // Remove if already exists, then add the new one
        tools.removeIf { it.authority == toolInfo.authority }
        tools.add(toolInfo)
        saveTools(tools)
    }

    override fun removeExternalTool(authority: String) {
        val tools = getRegisteredTools().toMutableList()
        tools.removeIf { it.authority == authority }
        saveTools(tools)
    }

    override fun getRegisteredTools(): List<ExternalToolInfo> {
        val toolsJson = prefs.getString(KEY_REGISTERED_TOOLS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ExternalToolInfoSerialized>>(toolsJson)
                .map { it.toExternalToolInfo() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun isToolRegistered(authority: String): Boolean {
        return getRegisteredTools().any { it.authority == authority }
    }

    private fun saveTools(tools: List<ExternalToolInfo>) {
        val serializedTools = tools.map { ExternalToolInfoSerialized.fromExternalToolInfo(it) }
        val toolsJson = json.encodeToString(serializedTools)
        prefs.edit().putString(KEY_REGISTERED_TOOLS, toolsJson).apply()
    }

    /**
     * Helper class for serialization of ExternalToolInfo
     */
    @kotlinx.serialization.Serializable
    private data class ExternalToolInfoSerialized(
        val authority: String,
        val toolName: String,
        val description: String,
        val inputSchemaJson: String,
        val requiredFields: List<String>
    ) {
        fun toExternalToolInfo(): ExternalToolInfo {
            return ExternalToolInfo(
                authority = authority,
                toolName = toolName,
                description = description,
                inputSchemaJson = inputSchemaJson,
                requiredFields = requiredFields
            )
        }

        companion object {
            fun fromExternalToolInfo(info: ExternalToolInfo): ExternalToolInfoSerialized {
                return ExternalToolInfoSerialized(
                    authority = info.authority,
                    toolName = info.toolName,
                    description = info.description,
                    inputSchemaJson = info.inputSchemaJson,
                    requiredFields = info.requiredFields
                )
            }
        }
    }
}
