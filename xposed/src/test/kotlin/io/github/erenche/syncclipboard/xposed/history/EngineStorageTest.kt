package io.github.erenche.syncclipboard.xposed.history

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun ordinaryFilesStayInDownloadDirectory() {
        assertEquals(File(folder.root, "a.txt").canonicalFile, EngineStorage.resolveDownloadFile(folder.root, "a.txt"))
    }

    @Test fun parentTraversalIsRejected() {
        for (name in listOf("../private", "sub/../../private", "..\\private")) {
            assertNotNull(runCatching { EngineStorage.resolveDownloadFile(folder.root, name) }.exceptionOrNull())
        }
    }

    @Test fun absoluteAndEmptyNamesAreRejected() {
        for (name in listOf(File(folder.root, "absolute").path, "", ".")) {
            assertNotNull(runCatching { EngineStorage.resolveDownloadFile(folder.root, name) }.exceptionOrNull())
        }
    }
}
