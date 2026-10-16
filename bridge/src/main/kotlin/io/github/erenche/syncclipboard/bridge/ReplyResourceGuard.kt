package io.github.erenche.syncclipboard.bridge

/** 等待取消与回调并发时，回收未交给调用方的资源。 */
internal class ReplyResourceGuard<T : Any>(private val dispose: (T) -> Unit) {
    private var waiting = true
    private var pending: T? = null

    @Synchronized
    fun offer(value: T, complete: () -> Boolean) {
        if (pending === value) return
        if (!waiting || pending != null) {
            dispose(value)
            return
        }
        pending = value
        if (!complete()) {
            pending = null
            dispose(value)
        }
    }

    @Synchronized
    fun claim(value: T): T {
        check(pending === value) { "Reply resource already released" }
        pending = null
        waiting = false
        return value
    }

    @Synchronized
    fun close() {
        waiting = false
        pending?.let(dispose)
        pending = null
    }
}
