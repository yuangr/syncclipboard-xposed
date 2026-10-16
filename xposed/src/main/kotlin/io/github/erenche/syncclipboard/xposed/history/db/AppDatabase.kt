package io.github.erenche.syncclipboard.xposed.history.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.erenche.syncclipboard.xposed.history.EngineStorage

/**
 * Room 数据库 — 剪贴板历史持久化。
 *
 * 数据库文件位于 SystemUI 私有目录（filesDir/SyncClipboard/clipboard_history.db），
 * app 进程不直接访问，仍通过 Bridge IPC 查询。
 */
@Database(
    entities = [HistoryItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyItemDao(): HistoryItemDao

    companion object {
        private val instances = mutableMapOf<String, AppDatabase>()

        @Synchronized
        fun getInstance(context: Context, serverKey: String): AppDatabase {
            return instances.getOrPut(serverKey) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    // 绝对路径：落盘于 filesDir/SyncClipboard/（路径以 / 开头时 Room 不再用 databases/）
                    EngineStorage.databaseFile(context.applicationContext, serverKey)
                        .apply { parentFile?.mkdirs() }
                        .absolutePath
                ).build()
            }
        }

        /** 热重载前关闭所有服务器数据库。 */
        @Synchronized
        fun closeInstance() {
            instances.values.forEach { db -> runCatching { db.close() } }
            instances.clear()
        }
    }
}
