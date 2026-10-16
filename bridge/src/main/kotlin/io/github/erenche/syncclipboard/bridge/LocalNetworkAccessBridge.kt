package io.github.erenche.syncclipboard.bridge

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import io.github.erenche.syncclipboard.common.PackageNames
import io.github.erenche.syncclipboard.common.util.Logger

/** 向 system_server 同步局域网开关，接收端通过模块签名权限校验来源。 */
object LocalNetworkAccessBridge {
    const val ACTION = "io.github.erenche.syncclipboard.SET_SYSTEMUI_LOCAL_NETWORK_ACCESS"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_CALLBACK = "callback"

    fun send(context: Context, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 37) return
        runCatching {
            BridgeSecurity.sendBroadcast(context, Intent(ACTION).apply {
                setPackage(PackageNames.ANDROID)
                putExtra(EXTRA_ENABLED, enabled)
            })
        }.onFailure { Logger.warn("LocalNetBridge", "Failed to send local network setting: ${it.message}") }
    }

    /** 查询真实内核权限；启用参数为空时不修改设置。 */
    suspend fun status(context: Context, enabled: Boolean? = null): Bundle {
        if (Build.VERSION.SDK_INT < 37) return Bundle.EMPTY
        val reply = CompletableDeferred<Bundle>()
        return try {
            val intent = Intent(ACTION).setPackage(PackageNames.ANDROID)
            if (enabled != null) intent.putExtra(EXTRA_ENABLED, enabled)
            intent.putExtras(Bundle().apply {
                putBinder(EXTRA_CALLBACK, object : IBridgeCallback.Stub() {
                    override fun onReply(res: Bundle) {
                        if (BridgeSecurity.isTrustedCallback(context, PackageNames.ANDROID)) reply.complete(res)
                    }
                })
            })
            BridgeSecurity.sendBroadcast(context, intent)
            withTimeoutOrNull(5000) { reply.await() } ?: Bundle.EMPTY
        } finally { reply.cancel() }
    }
}
