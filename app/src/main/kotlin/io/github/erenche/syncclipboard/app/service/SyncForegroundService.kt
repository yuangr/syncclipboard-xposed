package io.github.erenche.syncclipboard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.erenche.syncclipboard.app.R
import io.github.erenche.syncclipboard.app.activity.MainActivity
import io.github.erenche.syncclipboard.common.clipboard.ShizukuClipboardAccessor
import io.github.erenche.syncclipboard.common.util.Logger
import io.github.erenche.syncclipboard.xposed.sync.SyncEngine

/**
 * 独立前台服务 — 用于免 Root / Shizuku 模式下的后台长连接护航与剪贴板同步。
 *
 * 核心机制：
 * 1. 进程保活：通过系统前台服务（FOREGROUND_SERVICE_TYPE_SPECIAL_USE）避免进程被系统 cgroup freeze 冻结，
 *    确保 WebSocket / SignalR 长连接在后台和锁屏期间持续在线收发数据。
 * 2. 剪贴板对接：为 SyncEngine 装配 [ShizukuClipboardAccessor]，在后台读写与监听系统剪贴板。
 * 3. 优雅启停：提供 start / stop 静态入口，支持根据 Shizuku 授权状态自适应更新通知提示。
 */
class SyncForegroundService : Service() {

    companion object {
        private const val TAG = "SyncForegroundService"
        private const val CHANNEL_ID = "sync_foreground_service"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private var shizukuAccessor: ShizukuClipboardAccessor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Logger.info(TAG, "onCreate: starting foreground service")
        createNotificationChannel()
        startInForeground("初始化剪贴板同步服务...")

        isRunning = true

        try {
            val accessor = ShizukuClipboardAccessor(applicationContext)
            shizukuAccessor = accessor

            if (!SyncEngine.isInitialized()) {
                SyncEngine.getInstance().initialize(applicationContext, accessor)
                Logger.info(TAG, "SyncEngine initialized with ShizukuClipboardAccessor")
            } else {
                SyncEngine.getInstance().clipboardAccessor = accessor
                Logger.info(TAG, "SyncEngine already initialized, updated clipboardAccessor to Shizuku")
            }

            updateNotification(
                if (accessor.isAvailable()) "正在后台双向同步剪贴板"
                else "服务运行中 (等待 Shizuku 授权)"
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to initialize SyncEngine in foreground service", e)
            updateNotification("同步服务异常: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.info(TAG, "onStartCommand flags=$flags, startId=$startId")
        shizukuAccessor?.let { accessor ->
            updateNotification(
                if (accessor.isAvailable()) "正在后台双向同步剪贴板"
                else "服务运行中 (等待 Shizuku 授权)"
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Logger.info(TAG, "onDestroy: stopping foreground service")
        isRunning = false
        try {
            if (SyncEngine.isInitialized()) {
                SyncEngine.getInstance().stop()
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Error stopping SyncEngine on destroy: ${e.message}")
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "剪贴板后台同步",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台 SignalR 长连接及 Shizuku 剪贴板监听常驻运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SyncClipboard 运行中")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startInForeground(initialText: String) {
        val notification = buildNotification(initialText)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun updateNotification(statusText: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {
        }
    }
}
