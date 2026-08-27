package se.premex.mcp.files.repositories

/**
 * Metadata for a file or directory exposed by the files tool.
 * Paths are virtual: they always start with a root name (e.g. "shared/DCIM/photo.jpg").
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String?,
)

/**
 * Content of a file read from storage, possibly truncated to a maximum byte count.
 */
class FileData(
    val bytes: ByteArray,
    val mimeType: String?,
    val totalSize: Long,
    val truncated: Boolean,
)
