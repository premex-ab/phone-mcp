package se.premex.mcp.camera.repositories

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
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

    override suspend fun takePhoto(
        cameraId: String?,
        quality: Int,
        flashMode: String?,
        focusMode: String?,
        whiteBalance: String?,
        zoomLevel: Float?,
        pictureSize: String?
    ): File? = withContext(Dispatchers.IO) {
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

        // Parse requested picture size or use the largest supported size
        val jpegSizes = streamConfigurationMap.getOutputSizes(android.graphics.ImageFormat.JPEG)
        val selectedSize = if (pictureSize != null) {
            try {
                val parts = pictureSize.split("x")
                val width = parts[0].toInt()
                val height = parts[1].toInt()

                // Find the closest matching size
                jpegSizes.firstOrNull { it.width == width && it.height == height }
                    ?: jpegSizes.maxByOrNull { it.width * it.height }
                    ?: return@withContext null
            } catch (e: Exception) {
                jpegSizes.maxByOrNull { it.width * it.height }
                    ?: return@withContext null
            }
        } else {
            jpegSizes.maxByOrNull { it.width * it.height }
                ?: return@withContext null
        }

        // Start a background thread for camera operations
        val cameraThread = HandlerThread("CameraThread").apply { start() }
        val cameraHandler = Handler(cameraThread.looper)

        try {
            // Set up the ImageReader for capturing the photo
            val imageReader = ImageReader.newInstance(
                selectedSize.width,
                selectedSize.height,
                android.graphics.ImageFormat.JPEG,
                2
            )

            // Create output file
            val photoFile = createTempPhotoFile()

            // Use the ImageReader to save the captured image
            val imageSavedSemaphore = Semaphore(0)
            val focusLockSemaphore = Semaphore(0)

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

                // Set JPEG quality
                set(CaptureRequest.JPEG_QUALITY, quality.toByte())

                // Set the correct orientation
                val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                set(CaptureRequest.JPEG_ORIENTATION, rotation)

                // Set focus mode
                when (focusMode) {
                    "AUTO" -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    "CONTINUOUS_PICTURE" -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    "CONTINUOUS_VIDEO" -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    "EDOF" -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF)
                    "MACRO" -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO)
                    "OFF", "FIXED", "INFINITY" -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    "MANUAL" -> {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                        // If manual focus distance was provided, it could be set here
                    }
                    else -> set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }

                // Set flash mode if camera has flash
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    when (flashMode) {
                        "OFF" -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                        "SINGLE" -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                        }
                        "TORCH" -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        }
                        else -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        }
                    }
                }

                // Set white balance mode
                when (whiteBalance) {
                    "AUTO" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }
                    "CLOUDY_DAYLIGHT" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                    }
                    "DAYLIGHT" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
                    }
                    "FLUORESCENT" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
                    }
                    "INCANDESCENT" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
                    }
                    "SHADE" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_SHADE)
                    }
                    "TWILIGHT" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_TWILIGHT)
                    }
                    "WARM_FLUORESCENT" -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT)
                    }
                    else -> {
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }
                }

                // Set zoom level if zoom is supported
                if (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f > 1.0f) {
                    val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                    val requestedZoom = zoomLevel ?: 1.0f
                    val validatedZoom = when {
                        requestedZoom < 1.0f -> 1.0f
                        requestedZoom > maxZoom -> maxZoom
                        else -> requestedZoom
                    }

                    if (validatedZoom > 1.0f) {
                        // Get the active array size
                        val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        if (activeArraySize != null) {
                            // Calculate the zoom rect
                            val cropWidth = activeArraySize.width() / validatedZoom
                            val cropHeight = activeArraySize.height() / validatedZoom
                            val left = (activeArraySize.width() - cropWidth) / 2
                            val top = (activeArraySize.height() - cropHeight) / 2
                            val right = left + cropWidth
                            val bottom = top + cropHeight

                            val zoomRect = android.graphics.Rect(
                                left.toInt(),
                                top.toInt(),
                                right.toInt(),
                                bottom.toInt()
                            )
                            set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
                        }
                    }
                }
            }

            // Create a preview surface and capture session
            val previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(imageReader.surface)

                // Set up autofocus
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                // Trigger auto-focus
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            }

            // Run autofocus sequence first, then take picture
            captureSession.capture(previewBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)

                    // Check if auto-focus sequence is done and locked
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED) {
                        // Autofocus completed successfully
                        focusLockSemaphore.release()
                    } else if (afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                               afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED) {
                        // Couldn't focus but we'll take the picture anyway
                        focusLockSemaphore.release()
                    }
                }
            }, cameraHandler)

            // Wait for focus to be acquired (or timeout after 1 second)
            focusLockSemaphore.tryAcquire(1000, TimeUnit.MILLISECONDS)

            // Now capture the photo with established focus
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
