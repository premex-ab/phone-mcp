package se.premex.mcp.input.repositories

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class InputAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: InputAccessibilityService? = null

        fun getInstance(): InputAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for gesture simulation
    }

    override fun onInterrupt() {
        // Not needed for gesture simulation
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun performClick(x: Int, y: Int): Boolean {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))

        val gesture = gestureBuilder.build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
            }
        }, null)
    }
}

