package io.github.erenche.syncclipboard.xposed.api

import io.github.erenche.syncclipboard.common.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkClientTest {
    @get:Rule val folder = TemporaryFolder()
    @Before fun disableAndroidLogging() { Logger.enabled = false }

    private fun http(status: HttpStatusCode, body: String): HttpClient = HttpClient(MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }) { install(ContentNegotiation) { json() } }

    @Test fun authenticationAndMissingEndpointAreNotSuccess() = runBlocking {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.NotFound)) {
            SyncClipboardHttpClient("https://example.test", httpClient = http(status, "{}")).use {
                assertNotNull(runCatching { it.testConnection() }.exceptionOrNull())
            }
        }
    }

    @Test fun unrelatedHttpServerIsNotAClipboardServer() = runBlocking {
        SyncClipboardHttpClient("https://example.test", httpClient = http(HttpStatusCode.OK, "<html>hello</html>")).use {
            assertNotNull(runCatching { it.testConnection() }.exceptionOrNull())
        }
    }

    @Test fun clipboardProtocolResponsePasses() = runBlocking {
        val body = """{"type":"Text","hash":"abc","text":"hello","hasData":false}"""
        SyncClipboardHttpClient("https://example.test", httpClient = http(HttpStatusCode.OK, body)).use { it.testConnection() }
    }

    @Test fun closingClientCancelsItsResources() {
        val transport = http(HttpStatusCode.OK, "{}")
        SyncClipboardHttpClient("https://example.test", httpClient = transport).close()
        assertFalse(transport.coroutineContext[Job]!!.isActive)
    }

    @Test fun validWebdavResponsePasses() = runBlocking {
        WebDAVClient("https://example.test/dav", "user", "password", http(HttpStatusCode.MultiStatus, "")).use {
            it.testConnection()
        }
    }

    @Test fun webdavChecksPropfindInsteadOfOrdinaryHttpSuccess() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("PROPFIND", request.method.value)
            assertEquals("0", request.headers["Depth"])
            assertNotNull(request.headers[HttpHeaders.Authorization])
            respond("", HttpStatusCode.OK)
        }
        WebDAVClient("https://example.test/dav", "user", "password", HttpClient(engine)).use {
            assertNotNull(runCatching { it.testConnection() }.exceptionOrNull())
        }
    }

    @Test fun webdavDownloadChecksLengthAndPreservesExistingFile() = runBlocking {
        val destination = folder.newFile("existing")
        destination.writeText("old")
        val engine = MockEngine { respond("short", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "100")) }
        WebDAVClient("https://example.test/dav", "user", "password", HttpClient(engine)).use {
            assertNotNull(runCatching { it.downloadFile("a b.txt", destination.path) }.exceptionOrNull())
        }
        assertEquals("old", destination.readText())
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test fun webdavDownloadStreamsAndReportsProgress() = runBlocking {
        val destination = folder.newFile("existing")
        val progress = mutableListOf<Float>()
        val engine = MockEngine { request ->
            assertEquals("/dav/file/a%20b.txt", request.url.encodedPath)
            respond("downloaded", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "10"))
        }
        WebDAVClient("https://example.test/dav", "user", "password", HttpClient(engine)).use {
            it.downloadFile("a b.txt", destination.path) { value -> progress.add(value) }
        }
        assertEquals("downloaded", destination.readText())
        assertEquals(1f, progress.last())
    }

    @Test fun webdavHttpErrorDoesNotOverwriteDestination() = runBlocking {
        val destination = folder.newFile("existing")
        destination.writeText("old")
        WebDAVClient("https://example.test", "u", "p", http(HttpStatusCode.Forbidden, "denied")).use {
            assertNotNull(runCatching { it.downloadFile("file", destination.path) }.exceptionOrNull())
        }
        assertEquals("old", destination.readText())
    }
}
