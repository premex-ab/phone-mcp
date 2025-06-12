package se.premex.mcp.camera.repositories

interface CameraRepository {
    fun getCamerasInfo(): List<CameraInfo>
}
