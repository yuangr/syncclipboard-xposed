package io.github.erenche.syncclipboard.common.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Keystore 按宿主 UID 隔离，App 与 SystemUI 分别持有密钥。 */
object PreferenceEncryption {
    private const val ALIAS = "syncclipboard.preferences.v1"
    private var cachedKey: SecretKey? = null
    val codec = EncryptedValueCodec { getKey() }

    @Synchronized
    private fun getKey(): SecretKey {
        // 只缓存 Keystore 句柄，密钥材料不会进入进程内存。
        cachedKey?.let { return it }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { cachedKey = it; return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey().also { cachedKey = it }
    }
}
