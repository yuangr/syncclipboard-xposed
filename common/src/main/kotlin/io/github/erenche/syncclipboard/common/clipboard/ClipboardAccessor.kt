package io.github.erenche.syncclipboard.common.clipboard

import android.content.ClipData

/**
 * 剪贴板访问抽象接口。
 *
 * 允许在不同宿主环境无缝切换剪贴板交互方式：
 * 1. [SystemClipboardAccessor]: 运行在 SystemUI (LSPosed 模块模式) 或前台 Activity，直接使用系统 ClipboardManager
 * 2. [ShizukuClipboardAccessor]: 运行在普通 App 进程（免 Root/Shizuku 模式），通过 Shizuku Binder 代理访问系统 IClipboard
 */
interface ClipboardAccessor {
    /** 访问器名称，便于日志跟踪 */
    val name: String

    /** 当前是否可用（例如 Shizuku 服务已连接且已授权） */
    fun isAvailable(): Boolean

    /** 获取当前主剪贴板数据 */
    fun getPrimaryClip(): ClipData?

    /** 设置主剪贴板数据 */
    fun setPrimaryClip(clip: ClipData): Boolean

    /** 注册剪贴板变更监听器 */
    fun addPrimaryClipChangedListener(listener: OnPrimaryClipChangedListener)

    /** 注销剪贴板变更监听器 */
    fun removePrimaryClipChangedListener(listener: OnPrimaryClipChangedListener)

    fun interface OnPrimaryClipChangedListener {
        fun onPrimaryClipChanged()
    }
}
