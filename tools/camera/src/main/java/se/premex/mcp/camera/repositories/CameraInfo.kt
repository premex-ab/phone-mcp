package se.premex.mcp.camera.repositories

data class CameraInfo(
    val id: String,
    val facing: String,
    val orientation: Int,
    val supportedPictureSizes: List<String>,
    val supportedVideoSizes: List<String>,
    val supportedFlashModes: List<String>,
    val hasFlash: Boolean,
    val supportedFocusModes: List<String>,
    val supportedWhiteBalance: List<String>,
    val maxZoomLevel: Float,
    val isZoomSupported: Boolean
) {
    override fun toString(): String {
        return """
            Camera ID: $id
            Facing: $facing
            Orientation: $orientation°
            Supported Picture Sizes: ${supportedPictureSizes.joinToString(", ")}
            Supported Video Sizes: ${supportedVideoSizes.joinToString(", ")}
            Flash Supported: $hasFlash
            Supported Flash Modes: ${supportedFlashModes.joinToString(", ")}
            Supported Focus Modes: ${supportedFocusModes.joinToString(", ")}
            Supported White Balance Modes: ${supportedWhiteBalance.joinToString(", ")}
            Max Zoom Level: $maxZoomLevel
            Zoom Supported: $isZoomSupported
        """.trimIndent()
    }
}
