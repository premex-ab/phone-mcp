package se.premex.mcp.camera.appfunctions

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import se.premex.mcp.camera.repositories.CameraInfo
import se.premex.mcp.camera.repositories.CameraRepository

class CameraAppFunctionsTest {
    @Test
    fun capturePhoto_resolvesLensAndDelegatesSettings() = runBlocking {
        val expectedFile = File("captured.jpg")
        var capturedCameraId: String? = null
        var capturedQuality = 0
        val repository = object : CameraRepository {
            override fun getCamerasInfo(): List<CameraInfo> = listOf(
                cameraInfo(id = "0", facing = "Back"),
                cameraInfo(id = "1", facing = "Front"),
            )

            override suspend fun takePhoto(
                cameraId: String?,
                quality: Int,
                flashMode: String?,
                focusMode: String?,
                whiteBalance: String?,
                zoomLevel: Float?,
                pictureSize: String?,
            ): File {
                capturedCameraId = cameraId
                capturedQuality = quality
                return expectedFile
            }
        }

        val result = CameraAppFunctions(repository).capturePhoto("front", 92)

        assertSame(expectedFile, result)
        assertEquals("1", capturedCameraId)
        assertEquals(92, capturedQuality)
    }

    private fun cameraInfo(id: String, facing: String) = CameraInfo(
        id = id,
        facing = facing,
        orientation = 0,
        supportedPictureSizes = emptyList(),
        supportedVideoSizes = emptyList(),
        supportedFlashModes = emptyList(),
        hasFlash = false,
        supportedFocusModes = emptyList(),
        supportedWhiteBalance = emptyList(),
        maxZoomLevel = 1f,
        isZoomSupported = false,
    )
}
