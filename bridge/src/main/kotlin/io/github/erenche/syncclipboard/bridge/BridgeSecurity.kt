package io.github.erenche.syncclipboard.bridge

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import io.github.erenche.syncclipboard.common.PackageNames

/** 广播使用系统发送方身份；Binder UID 仅用于真实回调事务。 */
object BridgeSecurity {
    const val PERMISSION_SYSTEM_EVENT = "android.permission.STATUS_BAR_SERVICE"

    fun isTrustedSender(context: Context, receiver: BroadcastReceiver, permission: String): Boolean {
        val uid = if (Build.VERSION.SDK_INT >= 34) receiver.sentFromUid else -1
        val allowed = if (permission == SyncClipboardBridge.PERMISSION_BRIDGE) {
            setOf(PackageNames.APPLICATION)
        } else setOf(PackageNames.SYSTEM_UI, PackageNames.ANDROID)
        return BroadcastSenderPolicy.accepts(uid, trustedUid(context, uid, allowed),
            permission == SyncClipboardBridge.PERMISSION_BRIDGE || permission == PERMISSION_SYSTEM_EVENT)
    }

    fun isTrustedCallback(context: Context, expectedPackage: String): Boolean =
        trustedUid(context, Binder.getCallingUid(), setOf(expectedPackage))

    private fun trustedUid(context: Context, uid: Int, packages: Set<String>): Boolean =
        uid >= 0 && packages.any { pkg ->
            runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid == uid }.getOrDefault(false)
        }

    fun sendBroadcast(context: Context, intent: Intent, receiverPermission: String? = null) {
        if (Build.VERSION.SDK_INT >= 34) {
            context.sendBroadcast(intent, receiverPermission,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
        } else context.sendBroadcast(intent, receiverPermission)
    }
}
