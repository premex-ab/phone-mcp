package se.premex.mcp.camera.repositories

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size

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
}
