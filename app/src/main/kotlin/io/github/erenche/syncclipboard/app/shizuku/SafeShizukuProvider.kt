package io.github.erenche.syncclipboard.app.shizuku

import android.os.Bundle
import android.util.Log
import rikka.shizuku.ShizukuProvider

/**
 * 线程安全且防重入的 ShizukuProvider。
 *
 * 针对高版本 Android (如 Android 15/16/17 S26 Ultra) 的并发安全加固：
 * 当系统或 shizuku_server 瞬间通过多个 Binder 线程并发调用 handleSendBinder 时，
 * 原生 ShizukuProvider 会在竞争态下连续执行 onBinderReceived，导致第二次执行 unlinkToDeath 时
 * 抛出 NoSuchElementException 并使 Binder 握手中断。
 * 本 Provider 通过互斥锁与异常拦截，保证 Binder 握手的幂等性与进程稳定性。
 */
class SafeShizukuProvider : ShizukuProvider() {

    companion object {
        private const val TAG = "SafeShizukuProvider"
        private val callLock = Any()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        synchronized(callLock) {
            return try {
                super.call(method, arg, extras)
            } catch (t: Throwable) {
                Log.w(TAG, "Suppressed concurrent Binder exception in ShizukuProvider.call: ${t.message}", t)
                null
            }
        }
    }
}
