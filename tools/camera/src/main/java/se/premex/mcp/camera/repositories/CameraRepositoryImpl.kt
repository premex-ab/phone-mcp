package se.premex.mcp.camera.repositories

import android.content.Context
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraRepositoryImpl(
    private val context: Context
) : CameraRepository {

    companion object {
        private const val TAG = "CameraRepositoryImpl"
    }

    // Lazily initialize the camera executor
    private val cameraExecutor: Executor by lazy {
        ContextCompat.getMainExecutor(context)
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    override fun getCamerasInfo(): List<CameraInfo> {
        val cameraInfoList = mutableListOf<CameraInfo>()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        try {
            // Get camera provider
            val cameraProvider = cameraProviderFuture.get()

            // Get available camera infos
            val availableCameraInfos = cameraProvider.availableCameraInfos

            for (cameraInfo in availableCameraInfos) {
                // Extract Camera2 camera characteristics
                val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
                val cameraId = camera2CameraInfo.cameraId
                val characteristics = camera2CameraInfo.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING
                ) as Int?

                // Determine camera facing
                val facing = when (characteristics) {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }

                // Get sensor orientation
                val orientation = camera2CameraInfo.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION
                ) as Int? ?: 0

                // Get supported picture sizes using Camera2 interop
                val streamConfigMap = camera2CameraInfo.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) as android.hardware.camera2.params.StreamConfigurationMap?

                // Get picture sizes
                val pictureSizes =
                    streamConfigMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                        ?.map { "${it.width}x${it.height}" } ?: emptyList()

                // Get video sizes
                val videoSizes =
                    streamConfigMap?.getOutputSizes(android.media.MediaRecorder::class.java)
                        ?.map { "${it.width}x${it.height}" } ?: emptyList()

                // Check if flash is available
                val hasFlash = camera2CameraInfo.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
                ) as Boolean? ?: false

                // Get available flash modes
                val flashModes = if (hasFlash) {
                    listOf("OFF", "SINGLE", "TORCH")
                } else {
                    listOf("NONE")
                }

                // Define focus modes
                val focusModes =
                    listOf("AUTO", "CONTINUOUS_PICTURE", "CONTINUOUS_VIDEO", "OFF", "MANUAL")

                // Define white balance modes
                val whiteBalanceModes = listOf(
                    "AUTO", "CLOUDY_DAYLIGHT", "DAYLIGHT", "FLUORESCENT",
                    "INCANDESCENT", "SHADE", "TWILIGHT", "WARM_FLUORESCENT"
                )

                // Check zoom capability
                val maxZoomLevel = camera2CameraInfo.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
                ) as Float? ?: 1.0f

                val isZoomSupported = maxZoomLevel > 1.0f

                // Create and add CameraInfo
                val info = CameraInfo(
                    id = cameraId,
                    facing = facing,
                    orientation = orientation,
                    supportedPictureSizes = pictureSizes,
                    supportedVideoSizes = videoSizes,
                    supportedFlashModes = flashModes,
                    hasFlash = hasFlash,
                    supportedFocusModes = focusModes,
                    supportedWhiteBalance = whiteBalanceModes,
                    maxZoomLevel = maxZoomLevel,
                    isZoomSupported = isZoomSupported
                )

                cameraInfoList.add(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cameras info", e)
        }

        return cameraInfoList
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
        try {
            Log.d(TAG, "Taking photo with cameraId: $cameraId, quality: $quality")

            // Create output file for the photo
            val photoFile = createTempPhotoFile()

            // Get the camera provider
            val cameraProvider = getCameraProvider()

            // Configure camera selector based on camera ID or default to back camera
            val cameraSelector = configureCameraSelector(cameraId, cameraProvider)

            // Configure image capture use case
            val imageCapture = configureImageCapture(
                quality,
                flashMode,
                pictureSize
            )

            // Bind use cases and get camera
            val camera = bindUseCases(cameraProvider, cameraSelector, imageCapture)

            // Apply camera settings (zoom, focus mode, white balance)
            applyCameraSettings(camera, focusMode, whiteBalance, zoomLevel)

            // Take photo and wait for result
            val photo = capturePhoto(imageCapture, photoFile)

            // Shutdown camera (ensure this runs on main thread)
            withContext(Dispatchers.Main) {
                cameraProvider.unbindAll()
            }

            photo
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo", e)
            null
        }
    }

    /**
     * Creates a temporary file for storing the captured photo
     */
    private fun createTempPhotoFile(): File {
        val storageDir = context.cacheDir
        return File.createTempFile("photo_", ".jpg", storageDir)
    }

    /**
     * Get the camera provider using coroutines
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    continuation.resume(cameraProvider)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * Configure the camera selector based on camera ID or default to back camera
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    @OptIn(ExperimentalCamera2Interop::class)
    private fun configureCameraSelector(
        cameraId: String?,
        cameraProvider: ProcessCameraProvider
    ): CameraSelector {
        // If camera ID is provided, create a selector for that specific camera
        if (cameraId != null) {
            val availableCameraInfos = cameraProvider.availableCameraInfos
            for (cameraInfo in availableCameraInfos) {
                val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
                if (camera2CameraInfo.cameraId == cameraId) {
                    return CameraSelector.Builder()
                        .addCameraFilter { cameraList ->
                            cameraList.filter { candidate ->
                                val candidateInfo = Camera2CameraInfo.from(candidate)
                                candidateInfo.cameraId == cameraId
                            }
                        }
                        .build()
                }
            }
        }

        // Default to back camera
        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    /**
     * Configure image capture use case based on provided parameters
     */
    private fun configureImageCapture(
        quality: Int,
        flashMode: String?,
        pictureSize: String?
    ): ImageCapture {
        val builder = ImageCapture.Builder()
            .setJpegQuality(quality)

        // Set flash mode
        when (flashMode) {
            "OFF" -> builder.setFlashMode(ImageCapture.FLASH_MODE_OFF)
            "SINGLE" -> builder.setFlashMode(ImageCapture.FLASH_MODE_ON)
            "TORCH" -> builder.setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            else -> builder.setFlashMode(ImageCapture.FLASH_MODE_AUTO)
        }

        // Set target resolution based on picture size if provided
        if (!pictureSize.isNullOrEmpty()) {
            try {
                val parts = pictureSize.split("x")
                if (parts.size == 2) {
                    val width = parts[0].toInt()
                    val height = parts[1].toInt()
                    builder.setTargetResolution(android.util.Size(width, height))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing picture size: $pictureSize", e)
                builder.setTargetAspectRatio(AspectRatio.RATIO_16_9)
            }
        }

        return builder.build()
    }

    /**
     * Bind use cases to camera provider and get camera instance
     */
    private suspend fun bindUseCases(
        cameraProvider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        imageCapture: ImageCapture
    ): Camera = withContext(Dispatchers.Main) {
        try {
            // Unbind any existing use cases
            cameraProvider.unbindAll()

            // Bind use cases to camera and return the camera instance
            cameraProvider.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                cameraSelector,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            throw e
        }
    }

    /**
     * Apply camera settings like focus mode, white balance, and zoom
     */
    private suspend fun applyCameraSettings(
        camera: Camera,
        focusMode: String?,
        whiteBalance: String?,
        zoomLevel: Float?
    ) = withContext(Dispatchers.Main) {
        // Set camera control settings
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo

        // Apply focus mode
        when (focusMode) {
            "AUTO" -> {
                // For auto focus, we focus at the center of the frame

            }
            "CONTINUOUS_PICTURE", "CONTINUOUS_VIDEO" -> {
                // CameraX handles continuous focus automatically, nothing to do
            }
            "OFF", "MANUAL" -> {
                // Cancel any auto focus
                cameraControl.cancelFocusAndMetering()
            }
        }

        // Apply zoom level
        val validZoomLevel = zoomLevel ?: 1.0f
        if (validZoomLevel > 1.0f) {
            val maxZoom = cameraInfo.zoomState.value?.maxZoomRatio ?: 1.0f
            val clampedZoomLevel = minOf(validZoomLevel, maxZoom)
            cameraControl.setZoomRatio(clampedZoomLevel)
        }

        // White balance - unfortunately, CameraX doesn't expose direct white balance control yet
        // We'll need to use Camera2Interop for this in a future implementation
    }

    /**
     * Take a photo with the configured ImageCapture use case
     */
    private suspend fun capturePhoto(
        imageCapture: ImageCapture,
        photoFile: File
    ): File? {
        return suspendCancellableCoroutine { continuation ->
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")
                        continuation.resume(photoFile)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                        continuation.resume(null)
                    }
                }
            )

            continuation.invokeOnCancellation {
                Log.d(TAG, "Photo capture cancelled")
                // If coroutine is cancelled, we cannot abort an in-progress capture in the current CameraX API
            }
        }
    }
}
