package io.github.erenche.syncclipboard.common.util

import org.junit.Assert.*
import org.junit.Test

class SafeDiagnosticsTest {
    @Test fun omitsResponseBodyAndSecrets() {
        assertEquals("IllegalStateException (HTTP 401)", SafeDiagnostics.failure(
            IllegalStateException("HTTP 401: password=secret; 验证码123456")))
    }
    @Test fun omitsUrlCredentialsAndTokens() {
        assertEquals("IllegalArgumentException", SafeDiagnostics.failure(
            IllegalArgumentException("https://user:secret@host/?token=private")))
    }
    @Test fun doesNotFollowSensitiveCause() {
        assertEquals("RuntimeException", SafeDiagnostics.failure(RuntimeException(null,
            IllegalStateException("clipboard: 123456"))))
    }
    @Test fun toleratesMissingMessage() {
        assertEquals("RuntimeException", SafeDiagnostics.failure(RuntimeException()))
    }
}
