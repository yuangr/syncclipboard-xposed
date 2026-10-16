package io.github.erenche.syncclipboard.common.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ServerIdentityTest {
    private val server = ServerConfig(type = ServerType.syncclipboard, url = "https://example.test", username = "user")

    @Test fun namesAndPasswordsDoNotMoveExistingData() {
        assertEquals(server.storageKey(), server.copy(name = "renamed", password = "changed").storageKey())
    }

    @Test fun trailingSlashDoesNotChangeIdentity() {
        assertEquals(server.storageKey(), server.copy(url = "https://example.test/").storageKey())
    }

    @Test fun accountsAndEndpointsAreIsolated() {
        assertNotEquals(server.storageKey(), server.copy(username = "other").storageKey())
        assertNotEquals(server.storageKey(), server.copy(url = "https://other.test").storageKey())
        assertNotEquals(server.storageKey(), server.copy(type = ServerType.webdav).storageKey())
    }

    @Test fun bucketsAndPrefixesAreIsolated() {
        val s3 = server.copy(type = ServerType.s3, bucketName = "one", objectPrefix = "a")
        assertNotEquals(s3.storageKey(), s3.copy(bucketName = "two").storageKey())
        assertNotEquals(s3.storageKey(), s3.copy(objectPrefix = "b").storageKey())
    }

    @Test fun identitySurvivesSerialization() {
        assertEquals(server.storageKey(), Json.decodeFromString<ServerConfig>(Json.encodeToString(server)).storageKey())
        assertTrue(server.storageKey().matches(Regex("[a-f0-9]{64}")))
    }

    @Test fun noServerUsesLocalStorage() {
        assertEquals("local", AppConfig().serverStorageKey())
    }
}
