package se.premex.mcp.camera.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.service.AppFunction
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import se.premex.mcp.camera.repositories.CameraRepository
import javax.inject.Inject

/** Phone actions that capture images with the device camera. */
class CameraAppFunctions @Inject constructor(
    private val cameraRepository: CameraRepository,
) {
    /**
     * Take a photo with a requested camera lens and return a temporary content URI.
     *
     * @param appFunctionContext The Android execution context used for permission checks and URI sharing.
     * @param lens Camera direction: back, front, or external. Null uses back.
     * @param quality JPEG quality from 1 through 100. Null uses 80.
     * @return A content URI for the captured JPEG in the app's temporary cache.
     * @throws AppFunctionPermissionRequiredException If camera permission has not been granted.
     * @throws AppFunctionInvalidArgumentException If lens or quality is unsupported.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun takePhoto(
        appFunctionContext: AppFunctionContext,
        lens: String? = null,
        quality: Int? = null,
    ): Uri {
        val context = appFunctionContext.context
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw AppFunctionPermissionRequiredException(
                "Camera permission is required. Ask the user to enable Camera and grant permission.",
            )
        }

        val photo = capturePhoto(lens ?: "back", quality ?: 80)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.camera-files",
            photo,
        )
    }

    internal suspend fun capturePhoto(lens: String, quality: Int): java.io.File {
        if (quality !in 1..100) {
            throw AppFunctionInvalidArgumentException("quality must be between 1 and 100.")
        }
        val normalizedLens = lens.lowercase()
        if (normalizedLens !in setOf("back", "front", "external")) {
            throw AppFunctionInvalidArgumentException(
                "lens must be one of: back, front, external.",
            )
        }
        val camera = cameraRepository.getCamerasInfo()
            .firstOrNull { it.facing.equals(normalizedLens, ignoreCase = true) }
            ?: throw AppFunctionInvalidArgumentException(
                "No $normalizedLens camera is available on this device.",
            )
        return cameraRepository.takePhoto(cameraId = camera.id, quality = quality)
            ?: throw AppFunctionAppUnknownException(
                "The camera could not capture a photo. Ask the user to retry with the app visible.",
            )
    }
}
