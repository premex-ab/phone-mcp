package se.premex.mcp.files.appfunctions

import android.util.Base64
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import se.premex.mcp.files.repositories.FileData
import se.premex.mcp.files.repositories.FileEntry
import se.premex.mcp.files.repositories.FilesRepository
import java.io.FileNotFoundException
import javax.inject.Inject

/** Metadata for a file or directory exposed by Phone MCP. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFunctionFileEntry(
    /** File or directory name. */
    val name: String,
    /** Virtual Phone MCP path, such as shared/Download/report.pdf. */
    val path: String,
    /** True for a directory and false for a file. */
    val isDirectory: Boolean,
    /** File size in bytes, or zero for a directory. */
    val sizeBytes: Long,
    /** Last-modified time as Unix epoch milliseconds. */
    val lastModified: Long,
    /** MIME type when known. */
    val mimeType: String?,
)

/** Bounded file content suitable for an AppFunctions response. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFunctionFileContent(
    /** UTF-8 text or base64-encoded binary data, as indicated by encoding. */
    val content: String,
    /** Either utf-8 or base64. */
    val encoding: String,
    /** MIME type when known. */
    val mimeType: String?,
    /** Complete file size in bytes. */
    val totalSize: Long,
    /** True when content was limited to maxBytes. */
    val truncated: Boolean,
)

/** Phone actions that browse and read files exposed through Phone MCP's virtual roots. */
class FilesAppFunctions @Inject constructor(
    private val filesRepository: FilesRepository,
) {
    /**
     * List Phone MCP storage roots or the entries inside a directory.
     *
     * @param appFunctionContext The Android execution context for this function invocation.
     * @param path Directory path such as shared/Download. Null or blank lists available roots.
     * @return Files and directories ordered with directories first.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun listFiles(
        appFunctionContext: AppFunctionContext,
        path: String? = null,
    ): List<AppFunctionFileEntry> = executeListFiles(path)

    internal fun executeListFiles(path: String?): List<AppFunctionFileEntry> =
        executeFileOperation {
            val entries = if (path.isNullOrBlank()) {
                filesRepository.listRoots()
            } else {
                filesRepository.listFiles(path)
            }
            entries.map(FileEntry::toAppFunctionFileEntry)
        }

    /**
     * Get metadata for a file or directory.
     *
     * @param appFunctionContext The Android execution context for this function invocation.
     * @param path Virtual Phone MCP path such as shared/Download/report.pdf.
     * @return File or directory metadata.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun getFileInfo(
        appFunctionContext: AppFunctionContext,
        path: String,
    ): AppFunctionFileEntry = executeGetFileInfo(path)

    internal fun executeGetFileInfo(path: String): AppFunctionFileEntry = executeFileOperation {
        validatePath(path)
        filesRepository.getFileInfo(path).toAppFunctionFileEntry()
    }

    /**
     * Read a bounded portion of a file as UTF-8 text or base64-encoded binary data.
     *
     * AppFunctions responses are intentionally capped below Android's IPC transaction limit.
     * Use listFiles and getFileInfo first when selecting large files.
     *
     * @param appFunctionContext The Android execution context for this function invocation.
     * @param path Virtual Phone MCP path such as shared/Download/notes.txt.
     * @param maxBytes Maximum bytes to return, from 1 through 250000. Null uses 100000.
     * @return Bounded content with its encoding, MIME type, size, and truncation status.
     */
    @AppFunction(isDescribedByKDoc = true, isEnabled = false)
    suspend fun readFile(
        appFunctionContext: AppFunctionContext,
        path: String,
        maxBytes: Int? = null,
    ): AppFunctionFileContent = executeReadFile(path, maxBytes)

    internal fun executeReadFile(path: String, maxBytes: Int?): AppFunctionFileContent =
        executeFileOperation {
            validatePath(path)
            val requestedMaxBytes = maxBytes ?: DEFAULT_MAX_BYTES
            if (requestedMaxBytes !in 1..MAX_MAX_BYTES) {
                throw AppFunctionInvalidArgumentException(
                    "maxBytes must be between 1 and $MAX_MAX_BYTES.",
                )
            }
            filesRepository.readFile(path, requestedMaxBytes).toAppFunctionFileContent()
        }

    private fun validatePath(path: String) {
        if (path.isBlank()) {
            throw AppFunctionInvalidArgumentException("path must not be blank.")
        }
    }

    private fun <T> executeFileOperation(block: () -> T): T = try {
        block()
    } catch (exception: FileNotFoundException) {
        throw AppFunctionElementNotFoundException(exception.message ?: "File not found.")
    } catch (exception: SecurityException) {
        throw AppFunctionPermissionRequiredException(
            exception.message ?: "Storage permission is required.",
        )
    } catch (exception: IllegalArgumentException) {
        throw AppFunctionInvalidArgumentException(exception.message ?: "Invalid file path.")
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 100_000
        const val MAX_MAX_BYTES = 250_000
    }
}

private fun FileEntry.toAppFunctionFileEntry() = AppFunctionFileEntry(
    name = name,
    path = path,
    isDirectory = isDirectory,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    mimeType = mimeType,
)

private fun FileData.toAppFunctionFileContent(): AppFunctionFileContent {
    val isText = bytes.none { it == 0.toByte() }
    return AppFunctionFileContent(
        content = if (isText) {
            String(bytes, Charsets.UTF_8)
        } else {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        },
        encoding = if (isText) "utf-8" else "base64",
        mimeType = mimeType,
        totalSize = totalSize,
        truncated = truncated,
    )
}
