package io.github.erenche.syncclipboard.common.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.erenche.syncclipboard.common.util.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 原生系统剪贴板访问器。
 *
 * 适用于：
 * - 注入至 com.android.systemui 进程运行（LSPosed 模块）
 * - 具有系统级权限或处于前台获得焦点的环境
 */
class SystemClipboardAccessor(private val context: Context) : ClipboardAccessor {
    override val name: String = "SystemClipboard"

    private val clipboardManager: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val listeners = CopyOnWriteArrayList<ClipboardAccessor.OnPrimaryClipChangedListener>()

    private val systemListener = ClipboardManager.OnPrimaryClipChangedListener {
        for (l in listeners) {
            try {
                l.onPrimaryClipChanged()
            } catch (e: Exception) {
                Logger.warn(name, "Listener error: ${e.message}")
            }
        }
    }

    @Volatile
    private var registered = false

    override fun isAvailable(): Boolean = clipboardManager != null

    override fun getPrimaryClip(): ClipData? {
        return try {
            clipboardManager?.primaryClip
        } catch (e: Exception) {
            Logger.warn(name, "getPrimaryClip error: ${e.message}")
            null
        }
    }

    override fun setPrimaryClip(clip: ClipData): Boolean {
        return try {
            clipboardManager?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Logger.warn(name, "setPrimaryClip error: ${e.message}")
            false
        }
    }

    override fun addPrimaryClipChangedListener(listener: ClipboardAccessor.OnPrimaryClipChangedListener) {
        listeners.add(listener)
        if (!registered) {
            try {
                clipboardManager?.addPrimaryClipChangedListener(systemListener)
                registered = true
                Logger.info(name, "System OnPrimaryClipChangedListener registered")
            } catch (e: Exception) {
                Logger.warn(name, "Failed to register System listener: ${e.message}")
            }
        }
    }

    override fun removePrimaryClipChangedListener(listener: ClipboardAccessor.OnPrimaryClipChangedListener) {
        listeners.remove(listener)
        if (listeners.isEmpty() && registered) {
            try {
                clipboardManager?.removePrimaryClipChangedListener(systemListener)
            } catch (_: Exception) {
            }
            registered = false
            Logger.info(name, "System OnPrimaryClipChangedListener unregistered")
        }
    }
}
