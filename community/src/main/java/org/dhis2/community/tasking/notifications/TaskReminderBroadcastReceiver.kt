package org.dhis2.community.tasking.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.ui.TaskingStatus
import org.hisp.dhis.android.core.D2Manager
import timber.log.Timber
import java.util.Calendar
import java.util.Locale

/**
 * BroadcastReceiver that handles task reminder notifications.
 *
 * Receives intents from:
 * 1. AlarmManager - "org.dhis2.community.tasking.ALARM_ACTION"
 *    Triggers three times daily (7 AM, 12 PM, 5 PM) to post grouped notifications
 * 2. System Boot - "android.intent.action.BOOT_COMPLETED"
 *    Reschedules all three daily alarms after device reboot
 *
 * WhatsApp-Style Grouped Notifications:
 * - Queries database for all non-completed tasks this week
 * - Creates TaskNotificationSummary with status counts
 * - Posts group summary notification (ID 1000)
 * - Posts up to 10 child notifications (IDs 2000-2009)
 * - All grouped under GROUP_KEY_TASKS for automatic Android grouping
 *
 * Uses goAsync() for ~10 seconds of background work to query database.
 * Falls back gracefully if database unavailable.
 */
class TaskReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("TaskReminderBroadcastReceiver: onReceive() called with action: ${intent?.action}")

        if (context == null) {
            Timber.w("TaskReminderBroadcastReceiver: Context is null, cannot proceed")
            return
        }

        // Handle different intent actions
        when (intent?.action) {
            "android.intent.action.BOOT_COMPLETED" -> {
                Timber.d("TaskReminderBroadcastReceiver: BOOT_COMPLETED received, rescheduling all alarms")
                // Reschedule all three daily alarms after device reboot
                TaskReminderScheduler.scheduleTaskReminder(context)
            }
            "org.dhis2.community.tasking.ALARM_ACTION" -> {
                Timber.d("TaskReminderBroadcastReceiver: ALARM_ACTION received, posting notifications")
                // Use goAsync() to get extra time for background work
                // This gives us ~10 seconds to complete database queries and post notifications
                val result = goAsync()
                postNotificationAsync(context, result)
            }
            else -> {
                Timber.d("TaskReminderBroadcastReceiver: Unknown action: ${intent?.action}")
            }
        }
    }

    /**
     * Post WhatsApp-style grouped notifications asynchronously.
     *
     * Process:
     * 1. Create notification channels (required for API 26+)
     * 2. Check if notifications are enabled by user
     * 3. Query database for all tasks
     * 4. Filter for non-completed tasks this week (with overdue exemption)
     * 5. Sort by due date (most recent first)
     * 6. Build TaskNotificationSummary with status counts
     * 7. Post group summary notification (ID 1000) with .setGroupSummary(true)
     * 8. Post up to 10 child notifications (IDs 2000-2009) with .setGroupSummary(false)
     * 9. All grouped under GROUP_KEY_TASKS for automatic Android grouping
     * 10. Only summary makes sound/vibration (setGroupAlertBehavior(GROUP_ALERT_SUMMARY))
     *
     * Uses goAsync() to get ~10 seconds for database queries without blocking receiver.
     * Includes fallback notification if database is unavailable.
     *
     * @param context Application context
     * @param result PendingResult from goAsync() to finish when complete
     */
    private fun postNotificationAsync(
        context: Context,
        result: PendingResult
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            var notificationPosted = false

            try {
                Timber.d("TaskReminderBroadcastReceiver: Starting async notification post")

                // Create notification channels first
                NotificationChannelManager.createNotificationChannels(context)

                // Check if notifications are allowed
                if (!NotificationChannelManager.canPostNotifications(context)) {
                    Timber.w("TaskReminderBroadcastReceiver: Notifications are not enabled by user")
                    result.finish()
                    return@launch
                }

                try {
                    // Try to query task counts and individual tasks from database
                    Timber.d("TaskReminderBroadcastReceiver: Attempting to query D2 database for task counts")
                    val d2 = D2Manager.getD2()
                    val counter = TaskStatusCounter(d2)
                    val counts = counter.getTaskStatusCounts()

                    Timber.d(
                        "TaskReminderBroadcastReceiver: Task counts - Open: %d, DueSoon: %d, DueToday: %d, Overdue: %d",
                        counts.open, counts.dueSoon, counts.dueToday, counts.overdue
                    )

                    val notificationManager = NotificationManagerCompat.from(context)

                    // Cancel old child notifications before posting new batch
                    // This ensures old notifications don't mix with new ones and cleans up the notification group
                    try {
                        Timber.d("TaskReminderBroadcastReceiver: Cancelling old child notifications (IDs 2000-2999)")
                        // Cancel a wider range of IDs to handle more notifications
                        for (i in 0..999) {  // Cancel old IDs 2000-2999 (up to 1000 tasks)
                            notificationManager.cancel(TaskReminderNotificationBuilder.CHILD_NOTIFICATION_ID_START + i)
                        }
                        Timber.d("TaskReminderBroadcastReceiver: Old notifications cancelled")
                    } catch (e: Exception) {
                        Timber.w(e, "TaskReminderBroadcastReceiver: Could not cancel old notifications")
                    }

                    var childNotificationId = TaskReminderNotificationBuilder.CHILD_NOTIFICATION_ID_START  // Start from 2000

                    // Try to post individual child notifications (UNLIMITED - no cap)
                    try {
                        val repository = TaskingRepository(d2)
                        val allTasks = repository.getAllTasks()

                        // Filter for non-completed tasks this week and sort by DUE DATE DESCENDING (newest/2026 first)
                        val thisWeekTasks = allTasks
                            .filter { task ->
                                // Exclude completed and defaulted tasks
                                val status = task.status.trim().lowercase(Locale.US)
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

                        Timber.d("TaskReminderBroadcastReceiver: Found ${thisWeekTasks.size} tasks for this week, sorted by due date DESCENDING (newest first)")
                        Timber.d("TaskReminderBroadcastReceiver: Posting ${thisWeekTasks.size} child notifications with unique IDs")

                        for (task in thisWeekTasks) {
                            try {
                                // Calculate TaskingStatus using the same logic as TaskingUiModel
                                val taskingStatus = calculateTaskingStatus(task.status, task.dueDate)
                                val statusLabel = taskingStatus.label

                                val childNotification = TaskReminderNotificationBuilder.buildChildTaskNotification(
                                    context,
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
                                Timber.d("TaskReminderBroadcastReceiver: Posted child notification ID=$childNotificationId for ${task.name} (${task.dueDate}) Status=$statusLabel")
                                childNotificationId++
                            } catch (e: Exception) {
                                Timber.e(e, "TaskReminderBroadcastReceiver: Error posting child notification for ${task.name}")
                            }
                        }

                        Timber.d("TaskReminderBroadcastReceiver: Finished posting ${thisWeekTasks.size} child notifications - now posting SUMMARY LAST")
                        // Post group summary notification LAST with CONSTANT ID (1000)
                        notificationManager.notify(
                            TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID,
                            TaskReminderNotificationBuilder.buildTaskReminderNotification(
                                context,
                                thisWeekTasks
                            ).build()
                        )
                        Timber.d("TaskReminderBroadcastReceiver: Group summary notification posted LAST with CONSTANT ID ${TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID}")
                        notificationPosted = true

                    } catch (e: Exception) {
                        Timber.w(e, "TaskReminderBroadcastReceiver: Could not post individual child notifications")
                    }

                    // Post group summary notification LAST with CONSTANT ID (1000)
                    // According to Android docs, summary MUST be posted after all children
                    val summaryNotification = TaskReminderNotificationBuilder.buildTaskReminderNotification(
                        context,
                        counts
                    ).build()

                    notificationManager.notify(
                        TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID,  // Constant ID for grouping
                        summaryNotification
                    )

                    Timber.d("TaskReminderBroadcastReceiver: Group summary notification posted LAST with CONSTANT ID ${TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID}")
                    notificationPosted = true

                } catch (dbException: Exception) {
                    // D2 database not accessible in background - post fallback notification
                    Timber.w(dbException, "TaskReminderBroadcastReceiver: D2 database not accessible, posting fallback notification")

                    try {
                        // Post a simple fallback notification with zero counts
                        val fallbackCounts = TaskStatusCounts(open = 0, dueSoon = 0, dueToday = 0, overdue = 0)
                        val fallbackNotification = TaskReminderNotificationBuilder.buildTaskReminderNotification(
                            context,
                            fallbackCounts
                        ).build()

                        val notificationManager = NotificationManagerCompat.from(context)
                        notificationManager.notify(
                            TaskReminderNotificationBuilder.NOTIFICATION_GROUP_SUMMARY_ID,  // Constant ID
                            fallbackNotification
                        )

                        Timber.d("TaskReminderBroadcastReceiver: Fallback notification posted successfully (database unavailable)")
                        notificationPosted = true
                    } catch (fallbackException: Exception) {
                        Timber.e(fallbackException, "TaskReminderBroadcastReceiver: Failed to post fallback notification")
                    }
                }

                // Reschedule alarms for next occurrence
                // (Only needed if not using persistent alarms, but good practice)
                TaskReminderScheduler.scheduleTaskReminder(context)

            } catch (e: Exception) {
                Timber.e(e, "TaskReminderBroadcastReceiver: Unexpected error in postNotificationAsync")
            } finally {
                Timber.d("TaskReminderBroadcastReceiver: Notification posting complete. Posted: $notificationPosted")
                // Always finish the pending result
                result.finish()
            }
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
            val dueDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDateString) ?: return null
            Calendar.getInstance().apply {
                time = dueDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            Timber.w(e, "TaskReminderBroadcastReceiver: Error parsing due date: $dueDateString")
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
        val statusLower = apiStatus.trim().lowercase(Locale.US)
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
                    val dueDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDateString) ?: return TaskingStatus.OPEN

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
                    Timber.w(e, "TaskReminderBroadcastReceiver: Error parsing due date: $dueDateString")
                    TaskingStatus.OPEN
                }
            }
            else -> TaskingStatus.OPEN
        }
    }
}
