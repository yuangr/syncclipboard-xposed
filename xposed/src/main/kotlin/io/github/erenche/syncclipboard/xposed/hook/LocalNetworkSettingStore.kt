package io.github.erenche.syncclipboard.xposed.hook

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** 原子保存局域网开关；配置异常时不启用补权。 */
internal class LocalNetworkSettingStore(private val file: File) {
    @Volatile var enabled = false
        private set
    @Volatile var storageFailed = false
        private set
    private var persisted: Boolean? = null

    @Synchronized
    fun load() {
        enabled = false
        persisted = null
        try {
            val text = try {
                String(Files.readAllBytes(file.toPath()), Charsets.UTF_8).trim()
            } catch (_: NoSuchFileException) {
                null
            }
            persisted = when (text) {
                "1" -> true
                "0" -> false
                null -> null
                else -> throw IOException("Invalid LAN setting")
            }
            enabled = persisted == true
            storageFailed = false
        } catch (e: Exception) {
            storageFailed = true
            throw e
        }
    }

    @Synchronized
    fun update(requested: Boolean) {
        // 关闭立即生效；启用须先成功落盘。
        if (!requested) enabled = false
        try {
            if (persisted != requested || storageFailed) save(requested)
            persisted = requested
            enabled = requested
            storageFailed = false
        } catch (e: Exception) {
            storageFailed = true
            throw e
        }
    }

    private fun save(value: Boolean) {
        val target = file.absoluteFile.toPath()
        val temporary = Files.createTempFile(target.parent, ".syncclipboard-lan-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { stream ->
                stream.write(if (value) '1'.code else '0'.code)
                stream.fd.sync()
            }
            // 同目录原子替换，失败时保留旧文件。
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
