package io.github.erenche.syncclipboard.common.security

import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class EncryptedValueStoreTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val codec = EncryptedValueCodec { key }
    private val values = mutableMapOf<String, String>()
    private var writable = true
    private val store = EncryptedValueStore(codec, { values[it] }, { k, v ->
        if (writable) {
            if (v == null) values.remove(k) else values[k] = v
        }
        writable
    })

    @Test fun migratesPlaintextAfterValidation() {
        values["config"] = "legacy"
        assertEquals("legacy", store.load("config") { require(it == "legacy") })
        assertTrue(EncryptedValueCodec.isEncrypted(values.getValue("config")))
    }

    @Test fun preservesMalformedLegacyValue() {
        values["config"] = "invalid"
        assertThrows(Exception::class.java) { store.load("config") { error("Invalid config") } }
        assertEquals("invalid", values["config"])
    }

    @Test fun preservesPlaintextOnMigrationWriteFailure() {
        values["config"] = "legacy"
        writable = false
        assertThrows(Exception::class.java) { store.load("config") }
        assertEquals("legacy", values["config"])
    }

    @Test fun preservesCiphertextOnReadFailure() {
        val raw = "scenc:v1:broken"
        values["config"] = raw
        assertThrows(Exception::class.java) { store.load("config") }
        assertEquals(raw, values["config"])
    }

    @Test fun refusesPlaintextFallbackIfKeystoreFails() {
        values["config"] = "legacy"
        val unavailable = EncryptedValueStore(EncryptedValueCodec { error("Locked") },
            { values[it] }, { _, _ -> fail("Must not write"); true })
        assertThrows(Exception::class.java) { unavailable.save("config", "new secret") }
        assertEquals("legacy", values["config"])
    }

    @Test fun encryptedReadsDoNotRewrite() {
        store.save("config", "secret")
        val raw = values["config"]
        assertEquals("secret", store.load("config"))
        assertEquals(raw, values["config"])
    }

    @Test fun nullDeletesOnlyRequestedValue() {
        store.save("config", "secret")
        store.save("queue", "pending")
        store.save("config", null)
        assertNull(store.load("config"))
        assertEquals("pending", store.load("queue"))
    }
}
