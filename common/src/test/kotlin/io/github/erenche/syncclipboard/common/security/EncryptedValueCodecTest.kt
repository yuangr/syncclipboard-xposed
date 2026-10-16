package io.github.erenche.syncclipboard.common.security

import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class EncryptedValueCodecTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val codec = EncryptedValueCodec { key }

    @Test fun unicodeRoundTrip() {
        val plain = "密码与验证码：123456"
        val sealed = codec.encrypt(plain, "config")
        assertTrue(EncryptedValueCodec.isEncrypted(sealed))
        assertFalse(sealed.contains(plain))
        assertEquals(plain, codec.decrypt(sealed, "config"))
    }

    @Test fun emptyRoundTrip() {
        assertEquals("", codec.decrypt(codec.encrypt("", "config"), "config"))
    }

    @Test fun usesFreshNonce() {
        assertNotEquals(codec.encrypt("same", "config"), codec.encrypt("same", "config"))
    }

    @Test fun rejectsTampering() {
        val sealed = codec.encrypt("secret", "config")
        val payload = Base64.getDecoder().decode(sealed.removePrefix(EncryptedValueCodec.PREFIX))
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            codec.decrypt(EncryptedValueCodec.PREFIX + Base64.getEncoder().encodeToString(payload), "config")
        }
    }

    @Test fun rejectsDifferentKey() {
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(Exception::class.java) {
            EncryptedValueCodec { other }.decrypt(codec.encrypt("secret", "config"), "config")
        }
    }

    @Test fun rejectsDifferentPreferenceKey() {
        assertThrows(Exception::class.java) { codec.decrypt(codec.encrypt("secret", "config"), "queue") }
    }

    @Test fun rejectsUnknownVersion() {
        assertTrue(EncryptedValueCodec.isEncrypted("scenc:v2:value"))
        assertThrows(Exception::class.java) { codec.decrypt("scenc:v2:value", "config") }
    }

    @Test fun rejectsTruncatedPayload() {
        assertThrows(Exception::class.java) { codec.decrypt("scenc:v1:AQ==", "config") }
    }
}
