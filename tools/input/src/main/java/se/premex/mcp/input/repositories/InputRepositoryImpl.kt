package se.premex.mcp.input.repositories

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InputRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InputRepository {

    private var inputInfo = InputInfo()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "InputRepositoryImpl"

    override fun performClick(x: Int, y: Int): Boolean {
        var success = false

        try {
            val accessibilityService = InputAccessibilityService.getInstance()

            if (accessibilityService == null) {
                Log.e(TAG, "AccessibilityService is not enabled")
                showAccessibilityServiceNotEnabledMessage()
                inputInfo = InputInfo(x, y, false, "AccessibilityService not enabled")
                return false
            }

            success = accessibilityService.performClick(x, y)
            inputInfo = InputInfo(x, y, success)

        } catch (e: Exception) {
            e.printStackTrace()
            inputInfo = InputInfo(x, y, false, e.message ?: "Unknown error")
            return false
        }

        return success
    }

    private fun showAccessibilityServiceNotEnabledMessage() {
        mainHandler.post {
            Toast.makeText(
                context,
                "Please enable accessibility service for input control",
                Toast.LENGTH_LONG
            ).show()

            // Open accessibility settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    override fun getInputInfo(): InputInfo {
        return inputInfo
    }
}

