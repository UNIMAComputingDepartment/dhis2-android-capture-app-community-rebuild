package org.dhis2.community.tasking.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.ui.TaskingStatus
import org.hisp.dhis.android.core.D2Manager
import timber.log.Timber
import java.util.Calendar

/**
 * WorkManager-based task reminder notification worker.
 * Provides a reliable fallback for notification delivery if AlarmManager fails.
 *
 * WorkManager benefits:
 * - Survives app crashes and device reboots
 * - Respects battery optimization and data saver modes
 * - Queues work locally and retries on failure
 * - Works even if AlarmManager is restricted
 */
class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("TaskReminderWorker: Starting background task notification posting")

            // Delete old notification channel and recreate with fresh settings
            // This fixes any grouping issues caused by old channel config
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.deleteNotificationChannel(NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                    Timber.d("TaskReminderWorker: Deleted old notification channel for fresh recreation")
                } catch (e: Exception) {
                    Timber.w(e, "TaskReminderWorker: Could not delete old channel (might not exist)")
                }
            }

            // Recreate channel with correct settings
            NotificationChannelManager.createNotificationChannels(applicationContext)

            // Check if notifications are allowed
            if (!NotificationChannelManager.canPostNotifications(applicationContext)) {
                Timber.w("TaskReminderWorker: Notifications are not enabled by user")
                return Result.retry()
            }

            // Get D2 instance (DHIS2 SDK)
            val d2 = D2Manager.getD2()

            // Query task counts from database
            val counter = TaskStatusCounter(d2)
            val counts = counter.getTaskStatusCounts()

            Timber.d(
                "TaskReminderWorker: Task counts - Open: %d, DueSoon: %d, DueToday: %d, Overdue: %d",
                counts.open, counts.dueSoon, counts.dueToday, counts.overdue
            )

            val notificationManager = NotificationManagerCompat.from(applicationContext)

            // FIRST: Cancel old notifications
            try {
                Timber.d("TaskReminderWorker: Cancelling all old notifications (IDs 2000-2999 and summary 1000)")
                for (i in 0..999) {
                    notificationManager.cancel(TaskReminderNotificationBuilder.CHILD_NOTIFICATION_ID_START + i)
                }
                // Also cancel old summary
                notificationManager.cancel(TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID)
                Timber.d("TaskReminderWorker: All old notifications cancelled")
            } catch (e: Exception) {
                Timber.w(e, "TaskReminderWorker: Could not cancel old notifications")
            }

            // Wait for system to process cancellations
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Timber.w(e, "TaskReminderWorker: Interrupted during post-cancel delay")
            }

            // Post child notifications first (UNLIMITED - no cap)
            try {
                val repository = TaskingRepository(d2)
                val allTasks = repository.getAllTasks()

                // Filter for non-completed tasks this week and sort by DUE DATE DESCENDING (newest/2026 first)
                val thisWeekTasks = allTasks
                    .filter { task ->
                        // Exclude completed and defaulted tasks
                        val status = task.status.trim().lowercase()
                        if (status == "completed" || status == "defaulted") {
                            return@filter false
                        }

                        // Filter for this week or overdue
                        val dueDate = parseTaskDueDate(task.dueDate)
                        if (dueDate == null) {
                            return@filter false
                        }

                        val today = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        // Include overdue (before today) and this week (today to +7 days)
                        val endOfWeek = Calendar.getInstance().apply {
                            time = today.time
                            add(Calendar.DAY_OF_MONTH, 7)
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                        }

                        dueDate.before(endOfWeek) || dueDate.equals(today.time)
                    }
                    .sortedWith(compareBy { task ->
                        // Sort by DUE DATE DESCENDING (newest/2026 first)
                        val dueDate = parseTaskDueDate(task.dueDate)
                        if (dueDate != null) {
                            -dueDate.timeInMillis  // Negate for descending order (2026 first)
                        } else {
                            Long.MAX_VALUE
                        }
                    })

                Timber.d("TaskReminderWorker: Found ${thisWeekTasks.size} tasks for this week, sorted by due date DESCENDING (newest first)")
                Timber.d("TaskReminderWorker: Posting ${thisWeekTasks.size} child notifications with unique IDs")

                var childNotificationId = TaskReminderNotificationBuilder.CHILD_NOTIFICATION_ID_START
                for (task in thisWeekTasks) {
                    try {
                        // Calculate TaskingStatus using the same logic as TaskingUiModel
                        val taskingStatus = calculateTaskingStatus(task.status, task.dueDate)
                        val statusLabel = taskingStatus.label

                        val childNotification = TaskReminderNotificationBuilder.buildChildTaskNotification(
                            applicationContext,
                            taskName = task.name,
                            patientName = task.teiPrimary.takeIf { it.isNotBlank() } ?: "Unknown Patient",
                            taskStatus = statusLabel,
                            sourceTeiUid = task.sourceTeiUid,
                            sourceProgramUid = task.sourceProgramUid,
                            sourceEnrollmentUid = task.sourceEnrollmentUid,
                            iconName = task.iconNane,  // Use program icon from task (matches TaskingUiModel)
                            dueDate = task.dueDate,    // Due date in "yyyy-MM-dd" format
                            notificationId = childNotificationId
                        ).build()

                        notificationManager.notify(childNotificationId, childNotification)
                        Timber.d("TaskReminderWorker: Posted child notification ID=$childNotificationId for ${task.name} (${task.dueDate}) Status=$statusLabel")
                        childNotificationId++
                    } catch (e: Exception) {
                        Timber.e(e, "TaskReminderWorker: Error posting child notification for ${task.name}")
                    }
                }

                Timber.d("TaskReminderWorker: Finished posting ${thisWeekTasks.size} child notifications")

                // CRITICAL DELAY: Wait 1 second before posting summary
                // This gives Android time to recognize and group all child notifications
                // Without this delay, Android may not properly group the notifications
                try {
                    Timber.d("TaskReminderWorker: Waiting 1 second before posting summary (critical for grouping)")
                    Thread.sleep(1000)
                    Timber.d("TaskReminderWorker: Delay complete, now posting group summary")
                } catch (e: InterruptedException) {
                    Timber.w(e, "TaskReminderWorker: Thread interrupted during pre-summary delay")
                }

                // FOURTH: Post summary notification LAST with CONSTANT ID
                // This ensures Android recognizes it as the group summary and groups all children
                try {
                    val summaryNotification = TaskReminderNotificationBuilder.buildTaskReminderNotification(
                        applicationContext,
                        thisWeekTasks
                    ).build()

                    notificationManager.notify(
                        TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID,
                        summaryNotification
                    )
                    Timber.d("TaskReminderWorker: Posted group summary notification with ID=${TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID} for ${thisWeekTasks.size} child notifications")
                } catch (e: Exception) {
                    Timber.e(e, "TaskReminderWorker: Error posting summary notification")
                }

            } catch (e: Exception) {
                Timber.w(e, "TaskReminderWorker: Could not post individual child notifications")
            }

            // Reschedule both AlarmManager and WorkManager for next day
            TaskReminderScheduler.scheduleTaskReminder(applicationContext)
            TaskReminderWorkScheduler.scheduleTaskReminderWork(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TaskReminderWorker: Error posting notification, will retry")
            // Retry with backoff on failure
            Result.retry()
        }
    }

    /**
     * Parse due date string into a Calendar object.
     * Handles "yyyy-MM-dd" format and validation.
     *
     * @param dueDateString Due date string (e.g., "2026-02-20")
     * @return Calendar object at midnight, or null if invalid
     */
    private fun parseTaskDueDate(dueDateString: String?): Calendar? {
        if (dueDateString.isNullOrBlank()) {
            return null
        }

        // Check if this looks like an attribute ID (starts with letter)
        if (dueDateString.matches(Regex("[a-zA-Z].*"))) {
            return null
        }

        val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
        if (!dateRegex.matches(dueDateString)) {
            return null
        }

        return try {
            val dueDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dueDateString) ?: return null
            Calendar.getInstance().apply {
                time = dueDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            Timber.w(e, "TaskReminderWorker: Error parsing due date: $dueDateString")
            null
        }
    }

    /**
     * Calculate TaskingStatus using the same logic as TaskingUiModel.
     * Matches the status calculation in TaskingUiModel.calculateStatus().
     *
     * @param apiStatus Status from API (e.g., "completed", "open", "defaulted")
     * @param dueDateString Due date string in format "yyyy-MM-dd"
     * @return TaskingStatus enum matching the task's status
     */
    private fun calculateTaskingStatus(apiStatus: String, dueDateString: String?): TaskingStatus {
        val statusLower = apiStatus.trim().lowercase(java.util.Locale.US)
        return when (statusLower) {
            "completed" -> TaskingStatus.COMPLETED
            "defaulted" -> TaskingStatus.DEFAULTED
            "open" -> {
                // Only "open" status gets date-based calculation
                if (dueDateString.isNullOrBlank()) return TaskingStatus.OPEN

                // Check if this looks like an attribute ID (starts with letter)
                if (dueDateString.matches(Regex("[a-zA-Z].*"))) return TaskingStatus.OPEN

                val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
                if (!dateRegex.matches(dueDateString)) return TaskingStatus.OPEN

                return try {
                    val dueDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dueDateString) ?: return TaskingStatus.OPEN

                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val due = Calendar.getInstance().apply {
                        time = dueDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    when {
                        due.before(today) -> TaskingStatus.OVERDUE
                        due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                due.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> TaskingStatus.DUE_TODAY
                        due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                due.get(Calendar.DAY_OF_YEAR) in (today.get(Calendar.DAY_OF_YEAR) + 1)..(today.get(Calendar.DAY_OF_YEAR) + 3) -> TaskingStatus.DUE_SOON
                        else -> TaskingStatus.OPEN
                    }
                } catch (e: Exception) {
                    Timber.w(e, "TaskReminderWorker: Error parsing due date: $dueDateString")
                    TaskingStatus.OPEN
                }
            }
            else -> TaskingStatus.OPEN
        }
    }
}
