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

class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("TaskReminderWorker: Starting background task notification posting")

            // Setup notification channel and permissions (non-blocking UI thread since we're in CoroutineWorker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.deleteNotificationChannel(NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                    Timber.d("TaskReminderWorker: Deleted old notification channel")
                } catch (e: Exception) {
                    Timber.w(e, "TaskReminderWorker: Could not delete old channel (might not exist)")
                }
            }

            NotificationChannelManager.createNotificationChannels(applicationContext)

            if (!NotificationChannelManager.canPostNotifications(applicationContext)) {
                Timber.w("TaskReminderWorker: Notifications are not enabled by user")
                return Result.retry()
            }

            val d2 = D2Manager.getD2()
            val repository = TaskingRepository(d2)
            val notificationManager = NotificationManagerCompat.from(applicationContext)

            // OPTIMIZATION: Run all database queries on IO dispatcher to keep main thread free
            val allTasks = repository.getAllTasks()

            // Compute task status counts efficiently
            val counter = TaskStatusCounter(d2)
            val counts = counter.getTaskStatusCounts()

            Timber.d(
                "TaskReminderWorker: Task counts - Open: %d, DueSoon: %d, DueToday: %d, Overdue: %d",
                counts.open, counts.dueSoon, counts.dueToday, counts.overdue
            )

            // Cancel old notifications (batch operation)
            try {
                Timber.d("TaskReminderWorker: Cancelling all old notifications (IDs 2000-2999 and summary 1000)")
                for (i in 0..999) {
                    notificationManager.cancel(TaskReminderNotificationBuilder.CHILD_NOTIFICATION_ID_START + i)
                }
                notificationManager.cancel(TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID)
                Timber.d("TaskReminderWorker: All old notifications cancelled")
            } catch (e: Exception) {
                Timber.w(e, "TaskReminderWorker: Could not cancel old notifications")
            }

            // Post child notifications first (UNLIMITED - no cap)
            try {

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

                // Cap child notifications to max 10 to prevent notification spam
                val maxChildNotifications = 10
                val tasksToNotify = thisWeekTasks.take(maxChildNotifications)
                val totalTasksThisWeek = thisWeekTasks.size
                val notifiedTasksCount = tasksToNotify.size
                val skippedTasksCount = totalTasksThisWeek - notifiedTasksCount

                Timber.d("TaskReminderWorker: Posting $notifiedTasksCount child notifications (max: $maxChildNotifications, total this week: $totalTasksThisWeek${if (skippedTasksCount > 0) ", skipping $skippedTasksCount due to limit" else ""})")

                var childNotificationId = TaskReminderNotificationBuilder.CHILD_NOTIFICATION_ID_START
                for (task in tasksToNotify) {
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

                Timber.d("TaskReminderWorker: Finished posting $notifiedTasksCount child notifications")

                // Post summary notification LAST with CONSTANT ID
                // This ensures Android recognizes it as the group summary and groups all children
                try {
                    val summaryNotification = TaskReminderNotificationBuilder.buildTaskReminderNotification(
                        applicationContext,
                        tasksToNotify
                    ).build()

                    notificationManager.notify(
                        TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID,
                        summaryNotification
                    )
                    Timber.d("TaskReminderWorker: Posted group summary notification with ID=${TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID} for $notifiedTasksCount child notifications${if (skippedTasksCount > 0) " (note: $skippedTasksCount additional tasks not shown due to notification limit)" else ""}")
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
