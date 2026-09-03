package se.premex.mcp.files.appfunctions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.premex.mcp.files.repositories.FileData
import se.premex.mcp.files.repositories.FileEntry
import se.premex.mcp.files.repositories.FilesRepository

class FilesAppFunctionsTest {
    @Test
    fun executeListFiles_listsRootsWhenPathIsMissing() {
        val repository = FakeFilesRepository()

        val result = FilesAppFunctions(repository).executeListFiles(null)

        assertTrue(repository.listedRoots)
        assertEquals("shared", result.single().path)
    }

    @Test
    fun executeReadFile_returnsUtf8TextWithMetadata() {
        val repository = FakeFilesRepository()

        val result = FilesAppFunctions(repository).executeReadFile("shared/note.txt", 50)

        assertEquals("hello", result.content)
        assertEquals("utf-8", result.encoding)
        assertEquals("text/plain", result.mimeType)
        assertFalse(result.truncated)
        assertEquals(50, repository.requestedMaxBytes)
    }

    private class FakeFilesRepository(
        private val fileData: FileData = FileData("hello".toByteArray(), "text/plain", 5, false),
    ) : FilesRepository {
        var listedRoots = false
        var requestedMaxBytes = 0

        override fun listRoots(): List<FileEntry> {
            listedRoots = true
            return listOf(entry("shared"))
        }

        override fun listFiles(path: String): List<FileEntry> = listOf(entry(path))

        override fun getFileInfo(path: String): FileEntry = entry(path)

        override fun readFile(path: String, maxBytes: Int): FileData {
            requestedMaxBytes = maxBytes
            return fileData
        }

        private fun entry(path: String) = FileEntry(
            name = path.substringAfterLast('/'),
            path = path,
            isDirectory = true,
            sizeBytes = 0,
            lastModified = 1,
            mimeType = null,
        )
    }
}
