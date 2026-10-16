package io.github.erenche.syncclipboard.bridge

/** 迟到的旧配置不得覆盖新配置；重试同一版本允许幂等确认。 */
object ConfigRevisionPolicy {
    fun accepts(incoming: Long, applied: Long): Boolean = incoming > 0 && incoming >= applied
    fun acknowledged(expected: Long, actual: Long, success: Boolean): Boolean =
        success && expected > 0 && expected == actual
}
