package io.github.erenche.syncclipboard.xposed.sync

import io.github.erenche.syncclipboard.common.model.AppConfig
import io.github.erenche.syncclipboard.common.model.activeServer

/** 只根据当前服务器和推送开关决定是否重建连接。 */
internal object ClientUpdatePolicy {
    fun httpChanged(old: AppConfig, new: AppConfig): Boolean =
        old.activeServer()?.copy(name = null) != new.activeServer()?.copy(name = null)

    fun signalRChanged(old: AppConfig, new: AppConfig): Boolean =
        httpChanged(old, new) || old.enableSignalRPush != new.enableSignalRPush || old.enableAutoSync != new.enableAutoSync
}
