package io.github.erenche.syncclipboard.xposed.hook

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNetworkPermissionPolicyTest {
    @Test fun keepsExistingBitsAndUsesTheBackendMask() {
        assertEquals(0x85, LocalNetworkPermissionPolicy.supplement(0x81, 0x04, true))
        assertEquals(0x91, LocalNetworkPermissionPolicy.supplement(0x81, 0x10, true))
    }

    @Test fun disablingPreservesANaturallyGrantedPermission() {
        assertEquals(0x85, LocalNetworkPermissionPolicy.supplement(0x85, 0x04, false))
        assertEquals(0x81, LocalNetworkPermissionPolicy.supplement(0x81, 0x04, false))
    }

    @Test fun removalSentinelsNeverBecomeGrants() {
        assertEquals(-1, LocalNetworkPermissionPolicy.supplement(-1, 0x04, true))
        assertEquals(-2, LocalNetworkPermissionPolicy.supplement(-2, 0x04, true))
        assertEquals(1, LocalNetworkPermissionPolicy.supplement(1, 0, true))
    }

    @Test fun separatesAppIdMapsFromFullUidMaps() {
        assertEquals(10247, LocalNetworkPermissionPolicy.permissionId(1_010_247, true))
        assertEquals(1_010_247, LocalNetworkPermissionPolicy.permissionId(1_010_247, false))
    }

    @Test fun aBatchGrantOnlyChangesTheTarget() {
        val ids = intArrayOf(2000, 10247, 10300)
        assertArrayEquals(intArrayOf(2000, 10300), LocalNetworkPermissionPolicy.otherIds(ids, 10247))
        assertArrayEquals(intArrayOf(2000, 10247, 10300), ids)
        assertArrayEquals(intArrayOf(), LocalNetworkPermissionPolicy.otherIds(intArrayOf(10247), 10247))
    }
}
