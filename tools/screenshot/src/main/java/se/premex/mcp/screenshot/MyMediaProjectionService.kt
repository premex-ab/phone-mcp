package se.premex.mcp.screenshot


import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image.Plane
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import androidx.core.util.Pair
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import se.premex.mcp.screenshot.repositories.BitmapStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject


@AndroidEntryPoint
class MyMediaProjectionService : Service() {
    private var mMediaProjection: MediaProjection? = null

    private var mImageReader: ImageReader? = null
    private var mHandler: Handler? = null
    private var mDisplay: Display? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mDensity = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mRotation = 0
    private var mOrientationChangeCallback: OrientationChangeCallback? = null

    private var notificationManager: NotificationManager? = null

    private val mediaProjectionStopCallback = MediaProjectionStopCallback()

    @Inject
     lateinit var bitmapStorage: BitmapStorage

    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            try {
                mImageReader!!.acquireLatestImage().use { image ->
                    if (image != null) {
                        val planes: Array<Plane?> = image.planes
                        val buffer: ByteBuffer = planes[0]!!.buffer
                        val pixelStride: Int = planes[0]!!.pixelStride
                        val rowStride: Int = planes[0]!!.rowStride
                        val rowPadding = rowStride - pixelStride * mWidth

                        runBlocking {
                            bitmapStorage.store(buffer, mWidth, mHeight, rowPadding, pixelStride)
                        }

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private inner class OrientationChangeCallback(context: Context?) :
        OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = mDisplay!!.getRotation()
            if (rotation != mRotation) {
                mRotation = rotation
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay!!.release()

                    if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler!!.post {
                if (mVirtualDisplay != null) mVirtualDisplay!!.release()
                if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)
                if (mOrientationChangeCallback != null) mOrientationChangeCallback!!.disable()
                mMediaProjection!!.unregisterCallback(this@MediaProjectionStopCallback)
                // Stop the foreground service and remove notification
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize notification manager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (isStartCommand(intent)) {
            // Create and show notification
            val notification: Pair<Int, Notification> = NotificationUtils.getNotification(this)
            startForeground(notification.first, notification.second)

            // start projection
            val resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(DATA)
            startProjection(resultCode, data!!)
        } else if (isStopCommand(intent)) {
            stopProjection()
            stopForeground(true)
            stopSelf()
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data)
            if (mMediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().getDisplayMetrics().densityDpi
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = windowManager.getDefaultDisplay()

                // register media projection stop callback BEFORE creating virtual display
                mMediaProjection!!.registerCallback(
                    mediaProjectionStopCallback,
                    mHandler
                )

                // create virtual display depending on device width / height
                createVirtualDisplay()

                // register orientation change callback
                mOrientationChangeCallback = OrientationChangeCallback(this)
                if (mOrientationChangeCallback!!.canDetectOrientation()) {
                    mOrientationChangeCallback!!.enable()
                }
            }
        }
    }

    private fun stopProjection() {
        if (mHandler != null) {
            mHandler!!.post {
                if (mMediaProjection != null) {
                    mMediaProjection!!.stop()
                }
            }
        }
    }

    val cb = object : VirtualDisplay.Callback() {
        override fun onPaused() {
            Log.d(TAG, "VirtualDisplay paused")
        }

        override fun onResumed() {
            Log.d(TAG, "VirtualDisplay resumed")
        }

        override fun onStopped() {
            Log.d(TAG, "VirtualDisplay stopped")
            if (mMediaProjection != null) {
                mMediaProjection!!.unregisterCallback(mediaProjectionStopCallback)
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().getDisplayMetrics().widthPixels
        mHeight = Resources.getSystem().getDisplayMetrics().heightPixels

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            SCREENCAP_NAME, mWidth, mHeight,
            mDensity,
            virtualDisplayFlags, mImageReader!!.surface, cb, mHandler
        )


        mImageReader!!.setOnImageAvailableListener(
            ImageAvailableListener(),
            mHandler
        )
    }

    companion object {
        private const val TAG = "MyMediaProjectionService"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val SCREENCAP_NAME = "screencap"


        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent {
            val intent = Intent(context, MyMediaProjectionService::class.java)
            intent.putExtra(ACTION, START)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            return intent
        }

        fun getStopIntent(context: Context?): Intent {
            val intent = Intent(context, MyMediaProjectionService::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        private fun isStartCommand(intent: Intent): Boolean {
            return intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                    && intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == START
        }

        private fun isStopCommand(intent: Intent): Boolean {
            return intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == STOP
        }

        private val virtualDisplayFlags: Int
            get() = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }
}
