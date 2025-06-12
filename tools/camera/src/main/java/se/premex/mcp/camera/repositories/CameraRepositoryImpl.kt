package se.premex.mcp.camera.repositories

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraRepositoryImpl(
    private val context: Context
) : CameraRepository {

    override fun getCamerasInfo(): List<CameraInfo> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList

        return cameraIds.map { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            // Determine camera facing
            val facing = when(characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }

            // Get camera orientation
            val orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // Get supported picture sizes
            val pictureSizes = streamConfigurationMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.toList() ?: emptyList()
            val pictureSizesStr = pictureSizes.map { "${it.width}x${it.height}" }

            // Get supported video sizes
            val videoSizes = streamConfigurationMap?.getOutputSizes(android.media.MediaRecorder::class.java)?.toList() ?: emptyList()
            val videoSizesStr = videoSizes.map { "${it.width}x${it.height}" }

            // Check if flash is available
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

            // Get available flash modes (simplified - would need camera device to get actual modes)
            val flashModes = if (hasFlash) {
                listOf("OFF", "SINGLE", "TORCH")
            } else {
                listOf("NONE")
            }

            // Get available focus modes (simplified - would need camera device to get actual modes)
            val focusModes = listOf("AUTO", "CONTINUOUS_PICTURE", "CONTINUOUS_VIDEO", "EDOF", "FIXED", "INFINITY", "MACRO", "MANUAL")

            // Get white balance modes (simplified)
            val whiteBalanceModes = listOf("AUTO", "CLOUDY_DAYLIGHT", "DAYLIGHT", "FLUORESCENT", "INCANDESCENT", "SHADE", "TWILIGHT", "WARM_FLUORESCENT")

            // Check zoom capability
            val maxZoomLevel = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            val isZoomSupported = maxZoomLevel > 1.0f

            CameraInfo(
                id = cameraId,
                facing = facing,
                orientation = orientation,
                supportedPictureSizes = pictureSizesStr,
                supportedVideoSizes = videoSizesStr,
                supportedFlashModes = flashModes,
                hasFlash = hasFlash,
                supportedFocusModes = focusModes,
                supportedWhiteBalance = whiteBalanceModes,
                maxZoomLevel = maxZoomLevel,
                isZoomSupported = isZoomSupported
            )
        }
    }

    override suspend fun takePhoto(cameraId: String?, quality: Int): File? = withContext(Dispatchers.IO) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Use the provided cameraId or get the first available camera (preferring back camera)
        val actualCameraId = cameraId ?: getDefaultCameraId(cameraManager)
        if (actualCameraId == null) {
            return@withContext null
        }

        // Get characteristics to determine the optimal photo size
        val characteristics = cameraManager.getCameraCharacteristics(actualCameraId)
        val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return@withContext null

        // Get the largest supported size for JPEG format
        val jpegSizes = streamConfigurationMap.getOutputSizes(android.graphics.ImageFormat.JPEG)
        val previewSize = jpegSizes.maxByOrNull { it.width * it.height }
            ?: return@withContext null

        // Start a background thread for camera operations
        val cameraThread = HandlerThread("CameraThread").apply { start() }
        val cameraHandler = Handler(cameraThread.looper)

        try {
            // Set up the ImageReader for capturing the photo
            val imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                android.graphics.ImageFormat.JPEG,
                2
            )

            // Create output file
            val photoFile = createTempPhotoFile()

            // Use the ImageReader to save the captured image
            val imageSavedSemaphore = Semaphore(0)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)

                    // Save the image with specified quality
                    FileOutputStream(photoFile).use { output ->
                        output.write(bytes)
                    }

                    imageSavedSemaphore.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }, cameraHandler)

            // Open the camera
            val cameraOpenCloseSemaphore = Semaphore(1)
            cameraOpenCloseSemaphore.acquire()

            var cameraDevice: CameraDevice? = null

            // Open camera
            cameraDevice = openCamera(cameraManager, actualCameraId, cameraHandler, cameraOpenCloseSemaphore)
                ?: return@withContext null

            // Create a capture session and take a picture
            val captureSession = createCaptureSession(cameraDevice, imageReader.surface, cameraHandler)
                ?: return@withContext null

            // Create a capture request for taking the photo
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_QUALITY, quality.toByte())

                // Set the correct orientation
                val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                set(CaptureRequest.JPEG_ORIENTATION, rotation)
            }

            // Capture the photo
            captureSession.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                }
            }, cameraHandler)

            // Wait for image to be saved
            if (!imageSavedSemaphore.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                return@withContext null
            }

            // Close the camera and release resources
            cameraDevice.close()
            captureSession.close()

            return@withContext photoFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            cameraThread.quitSafely()
        }
    }

    /**
     * Gets the default camera ID (prefers back camera)
     */
    private fun getDefaultCameraId(cameraManager: CameraManager): String? {
        val cameraIds = cameraManager.cameraIdList
        if (cameraIds.isEmpty()) return null

        // Try to find a back-facing camera first
        for (id in cameraIds) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }

        // If no back-facing camera is found, return the first camera
        return cameraIds[0]
    }

    /**
     * Creates a temporary file for storing the captured photo
     */
    private fun createTempPhotoFile(): File {
        val storageDir = context.cacheDir
        return File.createTempFile("photo_", ".jpg", storageDir)
    }

    /**
     * Opens the camera device
     */
    private suspend fun openCamera(
        cameraManager: CameraManager,
        cameraId: String,
        handler: Handler,
        cameraOpenCloseSemaphore: Semaphore
    ): CameraDevice? = suspendCancellableCoroutine  { cont ->
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseSemaphore.release()
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseSemaphore.release()
                    camera.close()
                    cont.resume(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseSemaphore.release()
                    camera.close()
                    cont.resume(null)
                }
            }, handler)
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }

    /**
     * Creates a capture session for the camera
     */
    private suspend fun createCaptureSession(
        cameraDevice: CameraDevice,
        surface: Surface,
        handler: Handler
    ): CameraCaptureSession? = suspendCancellableCoroutine { cont ->
        try {
            cameraDevice.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cont.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        cont.resume(null)
                    }
                },
                handler
            )
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }
}
