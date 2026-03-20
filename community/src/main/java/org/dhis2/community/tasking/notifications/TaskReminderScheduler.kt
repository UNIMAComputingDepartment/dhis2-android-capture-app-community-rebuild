package org.dhis2.community.tasking.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber
import java.util.Calendar
import java.util.TimeZone

/**
 * Manages scheduling and cancellation of task reminder alarms.
 *
 * **Two-Times-Daily Scheduling**
 * Schedules alarms for two times daily in local time:
 * - 7:00 AM (Morning)
 * - 5:00 PM (Evening)
 *
 * Uses AlarmManager.setExactAndAllowWhileIdle() for reliable scheduling even in:
 * - Low-power modes
 * - Android Doze mode
 * - Power saving modes
 *
 * Notifications work WITHOUT opening the app (background alarms with goAsync()).
 * Alarms survive device reboots via BOOT_COMPLETED broadcast.
 */
object TaskReminderScheduler {
    // Request codes for two daily alarms (must be unique)
    private const val ALARM_REQUEST_CODE_MORNING = 9010  // 7:00 AM
    private const val ALARM_REQUEST_CODE_EVENING = 9012  // 5:00 PM
    fun scheduleTaskReminder(context: Context) {
        try {
            Timber.d("TaskReminderScheduler: Scheduling two-times-daily alarms (7:00 AM, 5:00 PM)")

            // Schedule morning and evening alarms
            scheduleAlarmForTime(context, 10, 10, ALARM_REQUEST_CODE_MORNING)    // 7:00 AM
            scheduleAlarmForTime(context, 10, 12, ALARM_REQUEST_CODE_EVENING)   // 5:00 PM

            Timber.d("TaskReminderScheduler: Both daily alarms scheduled successfully")

            // Schedule WorkManager as backup for maximum reliability
            Timber.d("TaskReminderScheduler: Scheduling backup WorkManager work")
            TaskReminderWorkScheduler.scheduleTaskReminderWork(context)

        } catch (e: Exception) {
            Timber.e(e, "TaskReminderScheduler: FATAL ERROR scheduling alarms")
        }
    }

    /**
     * Schedule a single alarm for a specific time of day.
     *
     * @param context Application context
     * @param hour Hour of day (0-23)
     * @param minute Minute of hour (0-59)
     * @param requestCode Unique request code for this alarm
     */
    private fun scheduleAlarmForTime(
        context: Context,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Create intent to trigger TaskReminderBroadcastReceiver
            val intent = Intent(context, TaskReminderBroadcastReceiver::class.java).apply {
                action = "org.dhis2.community.tasking.ALARM_ACTION"
            }

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Calculate next alarm time
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If time has already passed today, schedule for tomorrow
            val now = Calendar.getInstance()
            if (calendar.before(now)) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            Timber.d(
                "TaskReminderScheduler: Scheduling alarm for " +
                "${String.format("%02d:%02d", hour, minute)} " +
                "(next trigger: ${calendar.time})"
            )

            try {
                // Use setExactAndAllowWhileIdle for precise timing even in low-power mode
                // This wakes the device to deliver the notification
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )

                Timber.d("TaskReminderScheduler: Alarm scheduled successfully for ${String.format("%02d:%02d", hour, minute)}")

            } catch (e: SecurityException) {
                Timber.e(e, "TaskReminderScheduler: SCHEDULE_EXACT_ALARM permission denied. Falling back to flexible alarm.")
                // Fallback to inexact alarm if exact permission not granted
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Timber.d("TaskReminderScheduler: Fallback alarm scheduled (inexact) for ${String.format("%02d:%02d", hour, minute)}")
            }
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderScheduler: Error scheduling alarm for ${String.format("%02d:%02d", hour, minute)}")
        }
    }

    /**
     * Cancel all three daily task reminder alarms.
     *
     * @param context Application context
     */
    fun cancelTaskReminder(context: Context) {
        try {
            Timber.d("TaskReminderScheduler: Cancelling both daily alarms")

            val requestCodes = listOf(
                ALARM_REQUEST_CODE_MORNING,
                ALARM_REQUEST_CODE_EVENING
            )

            for (requestCode in requestCodes) {
                try {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val intent = Intent(context, TaskReminderBroadcastReceiver::class.java).apply {
                        action = "org.dhis2.community.tasking.ALARM_ACTION"
                    }

                    val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    }

                    alarmManager.cancel(pendingIntent)
                    Timber.d("TaskReminderScheduler: Cancelled alarm with request code $requestCode")
                } catch (e: Exception) {
                    Timber.w(e, "TaskReminderScheduler: Error cancelling alarm with request code $requestCode")
                }
            }

            Timber.d("TaskReminderScheduler: All alarms cancelled successfully")
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderScheduler: Error cancelling alarms")
        }
    }

    /**
     * Check if task reminder alarms are scheduled.
     *
     * @param context Application context
     * @return True if at least one alarm is scheduled, false otherwise
     */
    fun isTaskReminderScheduled(context: Context): Boolean {
        return try {
            val requestCodes = listOf(
                ALARM_REQUEST_CODE_MORNING,
                ALARM_REQUEST_CODE_EVENING
            )

            for (requestCode in requestCodes) {
                val intent = Intent(context, TaskReminderBroadcastReceiver::class.java).apply {
                    action = "org.dhis2.community.tasking.ALARM_ACTION"
                }

                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE
                    )
                }

                if (pendingIntent != null) {
                    return true  // At least one alarm is scheduled
                }
            }

            false  // No alarms scheduled
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderScheduler: Error checking alarm status")
            false
        }
    }

    /**
     * Trigger task reminder notification immediately for testing purposes.
     * Call this from MainActivity onCreate or anywhere to test notification grouping.
     * Creates 5 test notifications to verify grouping works.
     *
     * @param context Application context
     */
    fun triggerTaskReminderTest(context: Context) {
        try {
            Timber.d("TaskReminderScheduler: Manually triggering task reminder for testing")
            val intent = Intent(context, TaskReminderBroadcastReceiver::class.java).apply {
                action = "org.dhis2.community.tasking.ALARM_ACTION"
            }
            val receiver = TaskReminderBroadcastReceiver()
            receiver.onReceive(context, intent)
            Timber.d("TaskReminderScheduler: Test notification triggered successfully - check Timber logs for posting details")
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderScheduler: Error triggering test notification")
        }
    }
}
