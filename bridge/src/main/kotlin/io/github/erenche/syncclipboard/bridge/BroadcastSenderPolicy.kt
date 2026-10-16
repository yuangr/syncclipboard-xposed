package io.github.erenche.syncclipboard.bridge

/** 旧系统无发送方信息时，只允许受签名权限保护的接收器。 */
internal object BroadcastSenderPolicy {
    fun accepts(uid: Int, trustedUid: Boolean, signatureProtected: Boolean): Boolean =
        signatureProtected && (uid < 0 || trustedUid)
}
