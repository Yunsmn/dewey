package app.dewey.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * The notification a long-running job shows while it holds the foreground.
 *
 * Foreground work is a requirement rather than a courtesy: without it the system
 * kills a multi-minute indexing run partway through, and the user is left with
 * a half-built index and no explanation.
 */
class TaskNotifications(private val context: Context) {

    fun foregroundInfo(title: String, text: String, completed: Int, total: Int): ForegroundInfo {
        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (total > 0) {
                    setProgress(total, completed, false)
                    setSubText("$completed of $total")
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background work", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while Dewey indexes or sorts your documents"
                setShowBadge(false)
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "dewey.tasks"
        const val NOTIFICATION_ID = 1001
    }
}
