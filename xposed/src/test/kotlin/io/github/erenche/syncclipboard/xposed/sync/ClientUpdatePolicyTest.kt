package io.github.erenche.syncclipboard.xposed.sync

import io.github.erenche.syncclipboard.common.model.*
import org.junit.Assert.*
import org.junit.Test

class ClientUpdatePolicyTest {
    private val server = ServerConfig(type = ServerType.syncclipboard, url = "https://example.test")
    private val config = AppConfig(servers = listOf(server, server.copy(url = "https://other.test")), activeServerIndex = 0)

    @Test fun ordinarySettingsKeepBothClients() {
        val changed = config.copy(enableLogging = !config.enableLogging, enableHistorySync = !config.enableHistorySync,
            enableVerificationCodeAutoCleanup = !config.enableVerificationCodeAutoCleanup)
        assertFalse(ClientUpdatePolicy.httpChanged(config, changed))
        assertFalse(ClientUpdatePolicy.signalRChanged(config, changed))
    }

    @Test fun inactiveServerEditsKeepConnections() {
        val changed = config.copy(servers = listOf(server, server.copy(url = "https://third.test")))
        assertFalse(ClientUpdatePolicy.httpChanged(config, changed))
        assertFalse(ClientUpdatePolicy.signalRChanged(config, changed))
    }

    @Test fun activeServerSwitchRebuildsBoth() {
        assertTrue(ClientUpdatePolicy.httpChanged(config, config.copy(activeServerIndex = 1)))
        assertTrue(ClientUpdatePolicy.signalRChanged(config, config.copy(activeServerIndex = 1)))
    }

    @Test fun passwordChangeRebuildsConnectionsWithoutMovingData() {
        val changed = config.copy(servers = listOf(server.copy(password = "new")))
        assertTrue(ClientUpdatePolicy.httpChanged(config, changed))
        assertEquals(config.serverStorageKey(), changed.serverStorageKey())
    }

    @Test fun pushAndAutoSyncOnlyRebuildSignalR() {
        for (changed in listOf(config.copy(enableSignalRPush = false), config.copy(enableAutoSync = false))) {
            assertFalse(ClientUpdatePolicy.httpChanged(config, changed))
            assertTrue(ClientUpdatePolicy.signalRChanged(config, changed))
        }
    }

    @Test fun renamingDoesNotReconnect() {
        assertFalse(ClientUpdatePolicy.httpChanged(config, config.copy(servers = listOf(server.copy(name = "renamed")))))
    }
}
