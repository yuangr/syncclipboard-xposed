package io.github.erenche.syncclipboard.xposed.api

import java.io.File
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** 下载完成后才替换目标文件，失败或取消时保留原文件。 */
internal suspend fun downloadToFile(path: String, write: suspend (OutputStream) -> Unit): String {
    val destination = File(path).absoluteFile
    val directory = requireNotNull(destination.parentFile)
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create download directory" }
    val temporary = File.createTempFile(".syncclipboard-", ".part", directory)
    try {
        temporary.outputStream().buffered().use { write(it) }
        coroutineContext.ensureActive()
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return destination.path
    } finally {
        temporary.delete()
    }
}
