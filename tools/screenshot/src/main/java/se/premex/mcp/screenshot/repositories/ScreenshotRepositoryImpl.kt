package se.premex.mcp.screenshot.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.graphics.createBitmap

class ScreenshotRepositoryImpl(
    private val context: Context,
) : ScreenshotRepository {

    override suspend fun captureScreenshot(mediaProjection: MediaProjection): ScreenshotInfo = withContext(Dispatchers.IO) {


        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        val defaultDisplay = windowManager.defaultDisplay
        defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                val imageReader = ImageReader.newInstance(
                    width, height,
                    PixelFormat.RGBA_8888, 2
                )

                val handler = Handler(Looper.getMainLooper())
                val virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, handler
                )

                imageReader.setOnImageAvailableListener({ reader ->
                    var image: Image? = null
                    var bitmap: Bitmap? = null

                    try {
                        image = reader.acquireLatestImage()
                        if (image != null) {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            bitmap = createBitmap(width + rowPadding / pixelStride, height)
                            bitmap.copyPixelsFromBuffer(buffer)

                            // Convert bitmap to base64 string
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            val byteArray = outputStream.toByteArray()
                            val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

                            val screenshotInfo = ScreenshotInfo(
                                timestamp = System.currentTimeMillis(),
                                width = width,
                                height = height,
                                format = "jpeg",
                                base64Image = base64Image
                            )

                            continuation.resume(screenshotInfo)
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        image?.close()
                        bitmap?.recycle()
                        virtualDisplay?.release()
                        imageReader.close()
                    }
                }, handler)

                continuation.invokeOnCancellation {
                    imageReader.close()
                    virtualDisplay?.release()
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}
