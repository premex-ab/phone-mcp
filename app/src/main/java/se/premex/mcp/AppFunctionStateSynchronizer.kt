package se.premex.mcp

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appfunctions.AppFunctionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import se.premex.mcp.core.tool.McpTool
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps Android's AppFunction registry aligned with Phone MCP's tool switches. */
@Singleton
class AppFunctionStateSynchronizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun synchronize(
        tools: Set<McpTool>,
        toolEnabledStates: Map<String, Boolean>,
    ) {
        if (Build.VERSION.SDK_INT < 36) return

        val manager = try {
            AppFunctionManager.getInstance(context)
        } catch (exception: Exception) {
            Log.w(TAG, "AppFunctions are unavailable; tool state was not synchronized", exception)
            return
        } ?: return

        coroutineScope {
            desiredAppFunctionStates(tools, toolEnabledStates).map { (functionId, enabled) ->
                async {
                    val completed = withTimeoutOrNull(APP_FUNCTION_UPDATE_TIMEOUT_MILLIS) {
                        try {
                            manager.setAppFunctionEnabled(
                                functionId,
                                if (enabled) {
                                    AppFunctionManager.APP_FUNCTION_STATE_ENABLED
                                } else {
                                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED
                                },
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            Log.w(TAG, "Could not update AppFunction state for $functionId", exception)
                        }
                        true
                    }
                    if (completed == null) {
                        Log.w(TAG, "Timed out updating AppFunction state for $functionId")
                    }
                }
            }.awaitAll()
        }
    }

    private companion object {
        const val TAG = "AppFunctionStateSync"
        const val APP_FUNCTION_UPDATE_TIMEOUT_MILLIS = 3_000L
    }
}

internal fun desiredAppFunctionStates(
    tools: Set<McpTool>,
    toolEnabledStates: Map<String, Boolean>,
): Map<String, Boolean> = buildMap {
    tools.forEach { tool ->
        tool.appFunctionIds.forEach { functionId ->
            put(functionId, toolEnabledStates[tool.id] == true)
        }
    }
}
