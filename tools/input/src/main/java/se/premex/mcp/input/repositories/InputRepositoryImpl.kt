package se.premex.mcp.input.repositories

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InputRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InputRepository {

    private var inputInfo = InputInfo()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun performClick(x: Int, y: Int): Boolean {
        var success = false

        try {
            mainHandler.post {
                try {
                    // Use Instrumentation to inject a touch event
                    val downTime = System.currentTimeMillis()

                    // Create a transparent overlay view to capture and dispatch the touch event
                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                    // Create layout parameters for the overlay
                    val params = WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                    )
                    params.gravity = Gravity.TOP or Gravity.START

                    // Create the view that will dispatch the touch event
                    val touchView = View(context)
                    touchView.setOnTouchListener { _, _ -> true }

                    // Add the view to the window
                    windowManager.addView(touchView, params)

                    // Dispatch the touch events
                    val downEvent = MotionEvent.obtain(
                        downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0
                    )
                    touchView.dispatchTouchEvent(downEvent)
                    downEvent.recycle()

                    // Add a small delay before the up event
                    Thread.sleep(50)

                    val upEvent = MotionEvent.obtain(
                        downTime, System.currentTimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0
                    )
                    touchView.dispatchTouchEvent(upEvent)
                    upEvent.recycle()

                    // Remove the view
                    windowManager.removeView(touchView)

                    // Update input info
                    inputInfo = InputInfo(x, y, true)
                    success = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    inputInfo = InputInfo(x, y, false)
                    success = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            inputInfo = InputInfo(x, y, false)
            return false
        }

        // Give the UI thread a moment to execute the touch events
        Thread.sleep(100)
        return success
    }

    override fun getInputInfo(): InputInfo {
        return inputInfo
    }
}
