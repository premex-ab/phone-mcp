package se.premex.mcp.files.repositories

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class FilesRepositoryImpl(
    private val context: Context,
) : FilesRepository {

    private val roots: Map<String, File>
        get() = buildMap {
            Environment.getExternalStorageDirectory()?.let { put(SHARED_ROOT, it) }
            put(APP_FILES_ROOT, context.filesDir)
            put(APP_CACHE_ROOT, context.cacheDir)
            context.getExternalFilesDir(null)?.let { put(APP_EXTERNAL_ROOT, it) }
        }

    override fun listRoots(): List<FileEntry> = roots.map { (name, dir) ->
        FileEntry(
            name = name,
            path = name,
            isDirectory = true,
            sizeBytes = 0L,
            lastModified = dir.lastModified(),
            mimeType = null,
        )
    }

    override fun listFiles(path: String): List<FileEntry> {
        val (rootName, root, dir) = resolve(path)
        if (!dir.exists()) {
            throw FileNotFoundException("No such directory: $path")
        }
        if (!dir.isDirectory) {
            throw IllegalArgumentException("Not a directory: $path. Use phone_read_file to read files.")
        }
        val children = dir.listFiles()
            ?: throw SecurityException(
                "Cannot list '$path'. The directory is not readable with the app's current storage permissions."
            )
        return children
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { it.toEntry(rootName, root) }
    }

    override fun getFileInfo(path: String): FileEntry {
        val (rootName, root, file) = resolve(path)
        if (!file.exists()) {
            throw FileNotFoundException("No such file or directory: $path")
        }
        return file.toEntry(rootName, root)
    }

    override fun readFile(path: String, maxBytes: Int): FileData {
        val (_, _, file) = resolve(path)
        if (!file.exists()) {
            throw FileNotFoundException("No such file: $path")
        }
        if (file.isDirectory) {
            throw IllegalArgumentException("'$path' is a directory. Use phone_list_files to list it.")
        }
        if (!file.canRead()) {
            throw SecurityException(
                "Cannot read '$path'. The file is not readable with the app's current storage permissions."
            )
        }
        val totalSize = file.length()
        val buffer = ByteArray(maxBytes)
        var read = 0
        file.inputStream().use { input ->
            while (read < maxBytes) {
                val count = input.read(buffer, read, maxBytes - read)
                if (count < 0) break
                read += count
            }
        }
        return FileData(
            bytes = buffer.copyOf(read),
            mimeType = mimeTypeFor(file.name),
            totalSize = totalSize,
            truncated = read < totalSize,
        )
    }

    /**
     * Resolves a virtual path ("<root>/relative/path") to a canonical [File],
     * rejecting paths that escape their root via "..".
     */
    private fun resolve(path: String): Triple<String, File, File> {
        val normalized = path.trim().trim('/')
        if (normalized.isEmpty()) {
            throw IllegalArgumentException(
                "Empty path. Use one of the roots: ${roots.keys.joinToString()}"
            )
        }
        val rootName = normalized.substringBefore('/')
        val root = roots[rootName]
            ?: throw IllegalArgumentException(
                "Unknown root '$rootName'. Available roots: ${roots.keys.joinToString()}"
            )
        val canonicalRoot = root.canonicalFile
        val resolved = File(canonicalRoot, normalized.substringAfter('/', "")).canonicalFile
        if (resolved != canonicalRoot &&
            !resolved.path.startsWith(canonicalRoot.path + File.separator)
        ) {
            throw SecurityException("Path '$path' escapes the '$rootName' root.")
        }
        return Triple(rootName, canonicalRoot, resolved)
    }

    private fun File.toEntry(rootName: String, canonicalRoot: File): FileEntry {
        val relative = canonicalPath
            .removePrefix(canonicalRoot.path)
            .trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')
        return FileEntry(
            name = name,
            path = if (relative.isEmpty()) rootName else "$rootName/$relative",
            isDirectory = isDirectory,
            sizeBytes = if (isFile) length() else 0L,
            lastModified = lastModified(),
            mimeType = if (isFile) mimeTypeFor(name) else null,
        )
    }

    private fun mimeTypeFor(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    companion object {
        /** Shared storage visible to the user (e.g. /storage/emulated/0). */
        const val SHARED_ROOT = "shared"

        /** This app's private internal files directory. */
        const val APP_FILES_ROOT = "app-files"

        /** This app's private internal cache directory. */
        const val APP_CACHE_ROOT = "app-cache"

        /** This app's private directory on external storage. */
        const val APP_EXTERNAL_ROOT = "app-external"
    }
}
