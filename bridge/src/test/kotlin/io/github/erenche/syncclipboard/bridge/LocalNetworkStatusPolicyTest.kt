package io.github.erenche.syncclipboard.bridge

import org.junit.Assert.*
import org.junit.Test

class LocalNetworkStatusPolicyTest {
    private val status = LocalNetworkStatus(true, true, true, true, true, false)
    @Test fun lowApiIsVisibleButUnsupported() {
        assertFalse(LocalNetworkStatusPolicy.supported(36))
        assertEquals(LocalNetworkState.UNSUPPORTED, LocalNetworkStatusPolicy.classify(36, true, status))
    }
    @Test fun api37IsSupported() { assertTrue(LocalNetworkStatusPolicy.supported(37)) }
    @Test fun missingHookRequiresScopeCheckAndRestart() {
        assertEquals(LocalNetworkState.RESTART_REQUIRED, LocalNetworkStatusPolicy.classify(37, true, null))
    }
    @Test fun missingWriterWaitsRatherThanClaimsSuccess() {
        assertEquals(LocalNetworkState.WAITING, LocalNetworkStatusPolicy.classify(37, true, status.copy(writer = false)))
        assertEquals(LocalNetworkState.WAITING, LocalNetworkStatusPolicy.classify(37, true, status.copy(handler = false)))
    }
    @Test fun mismatchingDesiredStateIsPending() {
        assertEquals(LocalNetworkState.WAITING, LocalNetworkStatusPolicy.classify(37, false, status))
    }
    @Test fun failedReadbackNeverClaimsSuccess() {
        assertEquals(LocalNetworkState.FAILED, LocalNetworkStatusPolicy.classify(37, true, status.copy(readbackFailed = true)))
    }
    @Test fun failedPersistenceNeverClaimsEnableOrDisableSucceeded() {
        assertEquals(LocalNetworkState.FAILED,
            LocalNetworkStatusPolicy.classify(37, true, status.copy(storageFailed = true)))
        assertEquals(LocalNetworkState.FAILED,
            LocalNetworkStatusPolicy.classify(37, false, status.copy(enabled = false, storageFailed = true)))
    }
    @Test fun submittedWithoutVerificationIsNotApplied() {
        assertEquals(LocalNetworkState.FAILED, LocalNetworkStatusPolicy.classify(37, true, status.copy(verified = false)))
    }
    @Test fun reportsVerifiedEnableAndRestore() {
        assertEquals(LocalNetworkState.VERIFIED, LocalNetworkStatusPolicy.classify(37, true, status))
        assertEquals(LocalNetworkState.DISABLED, LocalNetworkStatusPolicy.classify(37, false, status.copy(enabled = false)))
    }
}
