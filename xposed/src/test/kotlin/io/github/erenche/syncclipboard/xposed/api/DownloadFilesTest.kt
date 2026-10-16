package io.github.erenche.syncclipboard.xposed.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun successfulDownloadReplacesDestination() = runBlocking {
        val destination = folder.newFile("existing")
        destination.writeText("old")
        downloadToFile(destination.path) { output ->
            val chunk = ByteArray(64 * 1024) { 7 }
            repeat(64) { output.write(chunk) }
        }
        assertEquals(4L * 1024 * 1024, destination.length())
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test fun failedDownloadPreservesDestinationAndRemovesTemporaryFile() = runBlocking {
        val destination = folder.newFile("existing")
        destination.writeText("old")
        val failure = runCatching {
            downloadToFile(destination.path) { it.write("partial".toByteArray()); throw IOException("interrupted") }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("old", destination.readText())
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test fun cancellationPreservesDestination() = runBlocking {
        val destination = folder.newFile("existing")
        destination.writeText("old")
        val failure = runCatching {
            downloadToFile(destination.path) { throw CancellationException("cancelled") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals("old", destination.readText())
        assertEquals(1, folder.root.listFiles()!!.size)
    }
}
