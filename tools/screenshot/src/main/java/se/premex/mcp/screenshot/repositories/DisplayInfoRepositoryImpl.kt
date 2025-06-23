package se.premex.mcp.screenshot.repositories

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import javax.inject.Inject

/**
 * Implementation of DisplayInfoRepository that retrieves display information from the device
 * Returns actual pixel dimensions of the screen
 */
class DisplayInfoRepositoryImpl @Inject constructor(
    private val context: Context
) : DisplayInfoRepository {

    override fun getDisplayInfo(): DisplayInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        val defaultDisplay = windowManager.defaultDisplay
        @Suppress("DEPRECATION")
        defaultDisplay.getMetrics(displayMetrics)

        return DisplayInfo(
            widthPixels = displayMetrics.widthPixels,
            heightPixels = displayMetrics.heightPixels
        )
    }
}
