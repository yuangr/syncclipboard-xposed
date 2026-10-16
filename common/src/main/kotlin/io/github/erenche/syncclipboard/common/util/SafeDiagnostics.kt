package io.github.erenche.syncclipboard.common.util

/** 不输出异常中的响应正文、凭据和剪贴板内容。 */
object SafeDiagnostics {
    private val httpStatus = Regex("\\bHTTP\\s+([1-5]\\d{2})\\b", RegexOption.IGNORE_CASE)

    fun failure(error: Throwable): String {
        val status = httpStatus.find(error.message.orEmpty())?.groupValues?.get(1)
        return error.javaClass.simpleName + (status?.let { " (HTTP $it)" } ?: "")
    }
}
