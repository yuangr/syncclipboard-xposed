package io.github.erenche.syncclipboard.bridge

import org.junit.Assert.*
import org.junit.Test

class BroadcastSenderPolicyTest {
    @Test fun allowsKnownTrustedSenderWithPermission() {
        assertTrue(BroadcastSenderPolicy.accepts(12345, true, true))
    }
    @Test fun rejectsKnownUntrustedSenderDespitePermission() {
        assertFalse(BroadcastSenderPolicy.accepts(12345, false, true))
    }
    @Test fun legacyIdentityRequiresSignatureFilter() {
        assertTrue(BroadcastSenderPolicy.accepts(-1, false, true))
    }
    @Test fun rejectsUnknownIdentityWithoutPermission() {
        assertFalse(BroadcastSenderPolicy.accepts(-1, false, false))
    }
    @Test fun rejectsUnprotectedReceiverEvenForTrustedUid() {
        assertFalse(BroadcastSenderPolicy.accepts(1000, true, false))
    }
}
