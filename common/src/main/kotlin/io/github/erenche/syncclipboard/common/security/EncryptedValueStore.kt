package io.github.erenche.syncclipboard.common.security

/** 明文迁移和新值写入均要求成功落盘，不回退到明文。 */
class EncryptedValueStore(
    private val codec: EncryptedValueCodec,
    private val read: (String) -> String?,
    private val write: (String, String?) -> Boolean
) {
    fun load(key: String, validate: (String) -> Unit = {}): String? {
        val raw = read(key) ?: return null
        val value = if (EncryptedValueCodec.isEncrypted(raw)) codec.decrypt(raw, key) else raw
        validate(value)
        if (!EncryptedValueCodec.isEncrypted(raw)) save(key, value)
        return value
    }

    fun save(key: String, value: String?) {
        val sealed = value?.let { codec.encrypt(it, key) }
        check(write(key, sealed)) { "Encrypted persistence failed" }
    }
}
