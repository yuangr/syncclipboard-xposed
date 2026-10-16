package io.github.erenche.syncclipboard.common.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 带版本和键名认证的加密存储格式。 */
class EncryptedValueCodec(private val key: () -> SecretKey) {
    fun encrypt(value: String, purpose: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    fun decrypt(value: String, purpose: String): String {
        require(value.startsWith(PREFIX)) { "Unsupported encrypted value" }
        val payload = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        require(payload.size >= 12 + 16) { "Invalid encrypted value" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }

    companion object {
        const val PREFIX = "scenc:v1:"
        fun isEncrypted(value: String): Boolean = value.startsWith("scenc:")
    }
}
