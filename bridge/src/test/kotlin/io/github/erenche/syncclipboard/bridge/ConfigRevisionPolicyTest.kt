package io.github.erenche.syncclipboard.bridge

import org.junit.Assert.*
import org.junit.Test

class ConfigRevisionPolicyTest {
    @Test fun acceptsNewerVersion() { assertTrue(ConfigRevisionPolicy.accepts(11, 10)) }
    @Test fun permitsIdempotentRetry() { assertTrue(ConfigRevisionPolicy.accepts(10, 10)) }
    @Test fun rejectsDelayedOldVersion() { assertFalse(ConfigRevisionPolicy.accepts(9, 10)) }
    @Test fun rejectsMissingRevision() { assertFalse(ConfigRevisionPolicy.accepts(0, 0)) }
    @Test fun onlyMatchingSuccessfulAckApplies() {
        assertTrue(ConfigRevisionPolicy.acknowledged(10, 10, true))
        assertFalse(ConfigRevisionPolicy.acknowledged(10, 9, true))
        assertFalse(ConfigRevisionPolicy.acknowledged(10, 10, false))
        assertFalse(ConfigRevisionPolicy.acknowledged(0, 0, true))
    }
}
