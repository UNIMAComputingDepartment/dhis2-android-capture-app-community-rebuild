package org.dhis2.community.tasking.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object TaskReminderWorkScheduler {
    // Work names for two daily tasks
    private const val WORK_NAME_MORNING = "task_reminder_work_morning"
    private const val WORK_NAME_EVENING = "task_reminder_work_evening"

    private const val MALAWI_TIMEZONE = "Africa/Johannesburg"

    private const val ALARM_HOUR_MORNING = 15
    private const val ALARM_MINUTE_MORNING = 31
    private const val ALARM_HOUR_EVENING = 15
    private const val ALARM_MINUTE_EVENING = 33

    fun scheduleTaskReminderWork(context: Context) {
        try {
            Timber.d("TaskReminderWorkScheduler: Scheduling two-times-daily work reminders (7:00 AM, 5:00 PM)")

            // Schedule work for both times
            scheduleWorkForTime(context, ALARM_HOUR_MORNING, ALARM_MINUTE_MORNING, WORK_NAME_MORNING)
            scheduleWorkForTime(context, ALARM_HOUR_EVENING, ALARM_MINUTE_EVENING, WORK_NAME_EVENING)

            Timber.d("TaskReminderWorkScheduler: Both WorkManager tasks scheduled successfully")
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderWorkScheduler: Error scheduling work")
        }
    }
    private fun scheduleWorkForTime(
        context: Context,
        hour: Int,
        minute: Int,
        workName: String
    ) {
        try {
            // Calculate initial delay to the first occurrence of this time
            val initialDelayMinutes = calculateInitialDelayMinutes(hour, minute)

            Timber.d("TaskReminderWorkScheduler: Scheduling $workName with ${initialDelayMinutes} minutes initial delay")

            // Create periodic work request for 24-hour repetition
            val taskReminderWork = PeriodicWorkRequestBuilder<TaskReminderWorker>(
                24, // Repeat every 24 hours
                TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

            // Enqueue with KEEP policy - don't cancel existing work if already scheduled
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                taskReminderWork
            )

            Timber.d("TaskReminderWorkScheduler: WorkManager periodic work scheduled for $workName at ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderWorkScheduler: Error scheduling work for ${String.format("%02d:%02d", hour, minute)}")
        }
    }

    fun cancelTaskReminderWork(context: Context) {
        try {
            Timber.d("TaskReminderWorkScheduler: Cancelling all two WorkManager tasks")

            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_MORNING)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_EVENING)

            Timber.d("TaskReminderWorkScheduler: All WorkManager tasks cancelled successfully")
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderWorkScheduler: Error cancelling work")
        }
    }
    private fun calculateInitialDelayMinutes(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(MALAWI_TIMEZONE)).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = Calendar.getInstance(TimeZone.getTimeZone(MALAWI_TIMEZONE))
        if (calendar.before(now)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val delayMillis = calendar.timeInMillis - now.timeInMillis
        return TimeUnit.MILLISECONDS.toMinutes(delayMillis)
    }

    fun triggerTaskReminderWorkTest(context: Context) {
        try {
            Timber.d("TaskReminderWorkScheduler: Manually triggering task reminder work for testing")

            // Enqueue immediate work request for testing
            val testWork = androidx.work.OneTimeWorkRequestBuilder<TaskReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(testWork)

            Timber.d("TaskReminderWorkScheduler: Test work enqueued successfully")
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderWorkScheduler: Error triggering test work")
        }
    }
}
