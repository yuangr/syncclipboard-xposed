package io.github.erenche.syncclipboard.bridge

enum class LocalNetworkState { UNSUPPORTED, UNKNOWN, RESTART_REQUIRED, WAITING, VERIFIED, DISABLED, FAILED }
data class LocalNetworkStatus(
    val enabled: Boolean, val backend: Boolean, val writer: Boolean,
    val handler: Boolean, val verified: Boolean, val readbackFailed: Boolean,
    val storageFailed: Boolean = false
)

object LocalNetworkStatusPolicy {
    fun supported(sdk: Int): Boolean = sdk >= 37

    fun classify(sdk: Int, desired: Boolean, status: LocalNetworkStatus?): LocalNetworkState = when {
        !supported(sdk) -> LocalNetworkState.UNSUPPORTED
        status == null -> LocalNetworkState.RESTART_REQUIRED
        status.readbackFailed || status.storageFailed -> LocalNetworkState.FAILED
        !status.backend || !status.writer || !status.handler -> LocalNetworkState.WAITING
        status.enabled != desired -> LocalNetworkState.WAITING
        !status.verified -> LocalNetworkState.FAILED
        desired -> LocalNetworkState.VERIFIED
        else -> LocalNetworkState.DISABLED
    }
}
