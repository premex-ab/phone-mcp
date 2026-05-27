package se.premex.mcp.appfunctions.configurator

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import se.premex.mcp.appfunctions.repositories.AppFunctionMetadataInfo
import se.premex.mcp.core.tool.AppFunctionParameterSpec
import se.premex.mcp.core.tool.AppFunctionSchemaMapper
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppFunctionsConfig"

/**
 * Pairs the stable bridge view of a discovered function with the platform metadata object.
 * The platform metadata is kept so we can use the public AppFunctionData.Builder constructor
 * that accepts List<AppFunctionParameterMetadata> + AppFunctionComponentsMetadata, since
 * AppFunctionData.Builder(qualifiedName, id) is marked @PublishedApi internal in alpha09.
 */
private data class DiscoveredFunction(
    val info: AppFunctionMetadataInfo,
    val platformMeta: AppFunctionMetadata,
)

@Singleton
class AppFunctionsConfiguratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppFunctionsConfigurator {

    override fun configureTools(server: Server) {
        // AppFunctions requires Android 16 (API 36)
        if (Build.VERSION.SDK_INT < 36) {
            Log.i(TAG, "AppFunctions unavailable on SDK < 36 (current=${Build.VERSION.SDK_INT}), skipping")
            return
        }

        // API renamed: was context.getSystemService(AppFunctionManager::class.java)
        //              now AppFunctionManager.getInstance(context) in alpha09
        val manager = try {
            AppFunctionManager.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain AppFunctionManager", e)
            null
        }
        if (manager == null) {
            Log.w(TAG, "AppFunctionManager unavailable on this device, skipping")
            return
        }

        val discovered = discoverAppFunctions(manager)
        Log.i(
            TAG,
            "Discovered ${discovered.size} AppFunctions across " +
                "${discovered.map { it.info.packageName }.toSet().size} packages",
        )

        discovered.forEach { discovered ->
            val info = discovered.info
            try {
                val schema = AppFunctionSchemaMapper.toMcpToolSchema(info.parameters)
                server.addTool(
                    name = info.mcpToolName,
                    description = info.description,
                    inputSchema = schema,
                ) { request ->
                    invokeAppFunction(manager, discovered, request.arguments ?: emptyMap())
                }
                Log.d(TAG, "Registered ${info.mcpToolName} from ${info.packageName}")
            } catch (e: Exception) {
                Log.w(TAG, "Skipping ${info.packageName}/${info.functionId}: ${e.message}")
            }
        }
    }

    /**
     * Calls observeAppFunctions and takes the first emission.
     *
     * API note: observeAppFunctions returns Flow<List<AppFunctionPackageMetadata>>,
     * not Flow<List<AppFunctionMetadata>> as the sample skeleton assumed. Each
     * AppFunctionPackageMetadata wraps a packageName + a list of AppFunctionMetadata.
     */
    private fun discoverAppFunctions(manager: AppFunctionManager): List<DiscoveredFunction> {
        return try {
            runBlocking {
                manager.observeAppFunctions(AppFunctionSearchSpec()).first()
                    .flatMap { packageMeta: AppFunctionPackageMetadata ->
                        packageMeta.appFunctions.mapNotNull { functionMeta ->
                            runCatching {
                                toDiscoveredFunction(packageMeta.packageName, functionMeta)
                            }
                                .onFailure {
                                    Log.w(
                                        TAG,
                                        "Skipping ${packageMeta.packageName}/${functionMeta.id}" +
                                            " with unsupported schema: ${it.message}",
                                    )
                                }
                                .getOrNull()
                        }
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AppFunction discovery failed", e)
            emptyList()
        }
    }

    private fun toDiscoveredFunction(
        packageName: String,
        meta: AppFunctionMetadata,
    ): DiscoveredFunction {
        val functionId = meta.id
        // Use the platform description if non-blank; synthesise a fallback so the required
        // non-null server.addTool description always has a value. description is non-null in
        // alpha09 but may be empty, hence the isNotBlank() guard (no ?. safe call needed).
        val description = meta.description.takeIf { it.isNotBlank() }
            ?: "AppFunction $functionId from $packageName"

        val params = meta.parameters.map { p: AppFunctionParameterMetadata ->
            AppFunctionParameterSpec(
                name = p.name,
                // API renamed: parameter type is AppFunctionDataTypeMetadata (abstract class)
                //              resolved via is-checks on the concrete subclass, not a string field
                type = mapPlatformDataType(p.dataType),
                // Pass through nullable description; AppFunctionSchemaMapper handles null
                description = p.description,
                required = p.isRequired,
            )
        }

        val info = AppFunctionMetadataInfo(
            packageName = packageName,
            functionId = functionId,
            mcpToolName = AppFunctionMetadataInfo.mcpToolNameFor(packageName, functionId),
            description = description,
            parameters = params,
        )
        return DiscoveredFunction(info = info, platformMeta = meta)
    }

    /**
     * Maps the platform's typed AppFunctionDataTypeMetadata subclass to our ParameterType enum.
     *
     * API note: the sample skeleton called mapPlatformType(p.dataType) treating dataType as Any
     * and doing toString().lowercase() matching. In alpha09 the type is AppFunctionDataTypeMetadata
     * (abstract), and the actual type is identified by the concrete subclass via is-checks.
     *
     * AppFunctionArrayTypeMetadata is only mapped to STRING_ARRAY when its itemType is a string.
     * Other array element types throw to trigger the "skip this function" path in discoverAppFunctions.
     */
    private fun mapPlatformDataType(
        dataType: AppFunctionDataTypeMetadata,
    ): AppFunctionParameterSpec.ParameterType {
        return when (dataType) {
            is AppFunctionStringTypeMetadata -> AppFunctionParameterSpec.ParameterType.STRING
            is AppFunctionIntTypeMetadata -> AppFunctionParameterSpec.ParameterType.INTEGER
            is AppFunctionLongTypeMetadata -> AppFunctionParameterSpec.ParameterType.LONG
            is AppFunctionBooleanTypeMetadata -> AppFunctionParameterSpec.ParameterType.BOOLEAN
            is AppFunctionDoubleTypeMetadata -> AppFunctionParameterSpec.ParameterType.NUMBER
            is AppFunctionFloatTypeMetadata -> AppFunctionParameterSpec.ParameterType.NUMBER
            is AppFunctionArrayTypeMetadata -> {
                val itemType = dataType.itemType
                if (itemType is AppFunctionStringTypeMetadata) {
                    AppFunctionParameterSpec.ParameterType.STRING_ARRAY
                } else {
                    throw IllegalArgumentException(
                        "Unsupported array item type: ${itemType::class.simpleName}",
                    )
                }
            }
            else -> throw IllegalArgumentException(
                "Unsupported AppFunction parameter type: ${dataType::class.simpleName}",
            )
        }
    }

    /**
     * Invokes the function via AppFunctionManager.executeAppFunction (suspend).
     *
     * API note: ExecuteAppFunctionRequest takes AppFunctionData (not JsonObject) for its
     * functionParameters argument. We build it via the public
     * AppFunctionData.Builder(List<AppFunctionParameterMetadata>, AppFunctionComponentsMetadata)
     * constructor (the String/String constructor is @PublishedApi internal in alpha09).
     * The response is a sealed interface: ExecuteAppFunctionResponse.Success / .Error.
     */
    private fun invokeAppFunction(
        manager: AppFunctionManager,
        discovered: DiscoveredFunction,
        arguments: Map<String, JsonElement>,
    ): CallToolResult {
        val info = discovered.info
        return try {
            val response = runBlocking {
                val functionParams = buildAppFunctionData(discovered, arguments)
                val request = ExecuteAppFunctionRequest(
                    targetPackageName = info.packageName,
                    functionIdentifier = info.functionId,
                    functionParameters = functionParams,
                )
                manager.executeAppFunction(request)
            }
            when (response) {
                is androidx.appfunctions.ExecuteAppFunctionResponse.Success ->
                    CallToolResult(content = listOf(TextContent(response.returnValue.toString())))
                is androidx.appfunctions.ExecuteAppFunctionResponse.Error ->
                    CallToolResult(
                        content = listOf(
                            TextContent("AppFunction error: ${response.error.message}"),
                        ),
                    )
            }
        } catch (e: AppFunctionException) {
            Log.w(TAG, "AppFunction ${info.packageName}/${info.functionId} threw", e)
            CallToolResult(content = listOf(TextContent("AppFunction error: ${e.message}")))
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error invoking ${info.packageName}/${info.functionId}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        "Function no longer available; restart the MCP server to refresh the tool list.",
                    ),
                ),
            )
        }
    }

    /**
     * Converts the JSON arguments map to AppFunctionData using typed setters.
     *
     * API note: the sample skeleton used argumentsToGenericDocument returning a JsonObject.
     * ExecuteAppFunctionRequest requires AppFunctionData. We use the public
     * Builder(List<AppFunctionParameterMetadata>, AppFunctionComponentsMetadata) constructor
     * so the builder knows the schema, then dispatch each value to the matching typed setter.
     */
    private fun buildAppFunctionData(
        discovered: DiscoveredFunction,
        arguments: Map<String, JsonElement>,
    ): androidx.appfunctions.AppFunctionData {
        val info = discovered.info
        val meta = discovered.platformMeta
        val paramsByName = info.parameters.associateBy { it.name }
        // Use the public constructor that accepts parameter metadata + components so the
        // builder's internal schema validation can work correctly.
        val builder = androidx.appfunctions.AppFunctionData.Builder(
            meta.parameters,
            meta.components,
        )
        arguments.forEach { (key, value) ->
            val spec = paramsByName[key]
            when {
                spec != null -> when (spec.type) {
                    AppFunctionParameterSpec.ParameterType.STRING ->
                        builder.setString(key, (value as? JsonPrimitive)?.content ?: value.toString())
                    AppFunctionParameterSpec.ParameterType.INTEGER -> {
                        val int = (value as? JsonPrimitive)?.intOrNull
                        if (int != null) builder.setInt(key, int)
                    }
                    AppFunctionParameterSpec.ParameterType.LONG -> {
                        val long = (value as? JsonPrimitive)?.longOrNull
                        if (long != null) builder.setLong(key, long)
                    }
                    AppFunctionParameterSpec.ParameterType.BOOLEAN -> {
                        val bool = (value as? JsonPrimitive)?.booleanOrNull
                        if (bool != null) builder.setBoolean(key, bool)
                    }
                    AppFunctionParameterSpec.ParameterType.NUMBER -> {
                        val d = (value as? JsonPrimitive)?.doubleOrNull
                        if (d != null) builder.setDouble(key, d)
                    }
                    AppFunctionParameterSpec.ParameterType.STRING_ARRAY -> {
                        val list = (value as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.content }
                            ?: emptyList()
                        builder.setStringList(key, list)
                    }
                }
                else ->
                    // Unknown parameter not in our spec; best-effort string coercion
                    builder.setString(key, (value as? JsonPrimitive)?.content ?: value.toString())
            }
        }
        return builder.build()
    }
}
