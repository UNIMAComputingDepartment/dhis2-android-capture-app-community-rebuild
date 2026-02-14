package org.dhis2.community.tasking.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber

object NotificationChannelManager {
    const val TASK_REMINDER_CHANNEL_ID = "task_reminders"
    private const val TASK_REMINDER_CHANNEL_NAME = "Task Reminders"
    private const val TASK_REMINDER_CHANNEL_DESC = "Notifications for upcoming task reminders"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    TASK_REMINDER_CHANNEL_ID,
                    TASK_REMINDER_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT  // ✅ CHANGED from IMPORTANCE_HIGH to enable grouping on Android 11+
                ).apply {
                    description = TASK_REMINDER_CHANNEL_DESC
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                }

                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Timber.d("NotificationChannelManager: Created task reminder notification channel with IMPORTANCE_DEFAULT for proper grouping on Android 11+")
            } catch (e: Exception) {
                Timber.e(e, "NotificationChannelManager: Error creating notification channel")
            }
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true
        }
    }
}
