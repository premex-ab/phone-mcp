package se.premex.mcp.camera.repositories

import java.io.File

interface CameraRepository {
    fun getCamerasInfo(): List<CameraInfo>

    /**
     * Takes a photo using the specified camera
     * @param cameraId The ID of the camera to use (null for default camera)
     * @param quality Photo quality (0-100)
     * @param flashMode Flash mode (OFF, SINGLE, TORCH)
     * @param focusMode Focus mode (AUTO, CONTINUOUS_PICTURE, etc.)
     * @param whiteBalance White balance mode (AUTO, DAYLIGHT, etc.)
     * @param zoomLevel Digital zoom level (1.0 = no zoom)
     * @param pictureSize Picture size in format "WIDTHxHEIGHT" (e.g. "4032x3024")
     * @return A File containing the captured image, or null if capture failed
     */
    suspend fun takePhoto(
        cameraId: String? = null,
        quality: Int = 80,
        flashMode: String? = null,
        focusMode: String? = null,
        whiteBalance: String? = null,
        zoomLevel: Float? = null,
        pictureSize: String? = null
    ): File?
}
