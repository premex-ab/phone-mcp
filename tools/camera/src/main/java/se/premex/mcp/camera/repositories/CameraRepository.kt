package se.premex.mcp.camera.repositories

import java.io.File

interface CameraRepository {
    fun getCamerasInfo(): List<CameraInfo>

    /**
     * Takes a photo using the specified camera
     * @param cameraId The ID of the camera to use (null for default camera)
     * @param quality Photo quality (0-100)
     * @return A File containing the captured image, or null if capture failed
     */
    suspend fun takePhoto(cameraId: String? = null, quality: Int = 80): File?
}
