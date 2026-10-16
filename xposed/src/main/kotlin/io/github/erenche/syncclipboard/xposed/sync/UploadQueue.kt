package io.github.erenche.syncclipboard.xposed.sync

import android.content.Context
import io.github.erenche.syncclipboard.common.Prefs
import io.github.erenche.syncclipboard.common.model.ClipboardContent
import io.github.erenche.syncclipboard.common.util.HashUtils
import io.github.erenche.syncclipboard.common.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 上传失败重试队列。
 *
 * 本地剪贴板内容上传失败时入队（不丢弃），网络恢复后由
 * 引擎触发重放，队列按服务器隔离，避免切换服务器后误上传。
 *
 * 持久化到 SharedPreferences（JSON）：SystemUI 进程重启后队列仍在。
 * 注意：文件类内容保存的是原始 content:// URI，重启后可能失去读取权限，
 * 重试时读取失败会跳过该条（尽力而为）。
 */
@Serializable
data class QueuedUpload(
    val content: ClipboardContent,
    val retryCount: Int = 0,
    val enqueuedAt: Long = System.currentTimeMillis()
)

object UploadQueue {
    private const val TAG = "UploadQueue"
    private const val KEY_QUEUE = "upload_queue"
    private const val MAX_QUEUE_SIZE = 20

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun prefs(context: Context) = Prefs.getPrefs(context)

    /** 内容唯一键：优先 profileHash，其次文本/文件 URI 的 sha256（与上传去重一致） */
    fun contentKey(content: ClipboardContent): String {
        return content.profileHash
            ?: if (content.hasData && content.fileUri != null) {
                HashUtils.sha256(content.fileUri!!)
            } else {
                HashUtils.sha256(content.text)
            }
    }

    fun isEmpty(context: Context, serverKey: String): Boolean = snapshot(context, serverKey).isEmpty()

    fun size(context: Context, serverKey: String): Int = snapshot(context, serverKey).size

    /** 读取队列快照（已持久化，仅内存操作） */
    @Synchronized
    fun snapshot(context: Context, serverKey: String): List<QueuedUpload> {
        return try {
            // 旧队列没有服务器归属，保留原键但不自动重放。
            val raw = Prefs.loadSensitiveString(context, "$KEY_QUEUE:$serverKey") ?: return emptyList()
            json.decodeFromString(ListSerializer(QueuedUpload.serializer()), raw)
        } catch (e: Exception) {
            // 不把读取失败当成空队列，避免后续入队覆盖原数据。
            Logger.warn(TAG, "Upload queue unavailable: ${e.javaClass.simpleName}")
            throw e
        }
    }

    /**
     * 入队。相同内容（同 key）只保留最新一条；超过上限丢弃最旧的。
     */
    @Synchronized
    fun enqueue(context: Context, serverKey: String, content: ClipboardContent) {
        val key = contentKey(content)
        val list = snapshot(context, serverKey).toMutableList()
        list.removeAll { contentKey(it.content) == key }
        list.add(QueuedUpload(content = content))
        val trimmed = list.takeLast(MAX_QUEUE_SIZE)
        persist(context, serverKey, trimmed)
        Logger.info(TAG, "Upload queued (size=${trimmed.size}, key=${key.take(12)}, textLength=${content.text.length})")
    }

    /** 取出队首（不移除） */
    fun peek(context: Context, serverKey: String): QueuedUpload? = snapshot(context, serverKey).firstOrNull()

    /** 从队列移除（上传成功后调用） */
    @Synchronized
    fun remove(context: Context, serverKey: String, key: String) {
        val original = snapshot(context, serverKey)
        val list = original.filterNot { contentKey(it.content) == key }
        if (list.size != original.size) {
            persist(context, serverKey, list)
            Logger.info(TAG, "Upload de-queued, remaining=${list.size}")
        }
    }

    /** 清空队列 */
    @Synchronized
    fun clear(context: Context) {
        val editor = prefs(context).edit()
        prefs(context).all.keys.filter { it == KEY_QUEUE || it.startsWith("$KEY_QUEUE:") }.forEach { editor.remove(it) }
        editor.apply()
    }

    /** 记录一次失败重试计数（达到上限后由调用方丢弃） */
    @Synchronized
    fun markRetry(context: Context, serverKey: String, key: String) {
        val list = snapshot(context, serverKey).map { item ->
            if (contentKey(item.content) == key) {
                item.copy(retryCount = item.retryCount + 1)
            } else {
                item
            }
        }
        persist(context, serverKey, list)
    }

    private fun persist(context: Context, serverKey: String, list: List<QueuedUpload>) {
        try {
            Prefs.saveSensitiveString(context, "$KEY_QUEUE:$serverKey",
                json.encodeToString(ListSerializer(QueuedUpload.serializer()), list))
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to persist upload queue: ${io.github.erenche.syncclipboard.common.util.SafeDiagnostics.failure(e)}")
        }
    }
}
