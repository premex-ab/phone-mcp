package se.premex.mcp.files.repositories

interface FilesRepository {
    /** Lists the top-level storage roots that can be browsed. */
    fun listRoots(): List<FileEntry>

    /** Lists the entries of the directory at the given virtual path. */
    fun listFiles(path: String): List<FileEntry>

    /** Returns metadata for the file or directory at the given virtual path. */
    fun getFileInfo(path: String): FileEntry

    /** Reads at most [maxBytes] bytes of the file at the given virtual path. */
    fun readFile(path: String, maxBytes: Int): FileData
}
