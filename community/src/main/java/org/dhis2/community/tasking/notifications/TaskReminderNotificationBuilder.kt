package org.dhis2.community.tasking.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import org.dhis2.community.R
import timber.log.Timber

/**
 * Builds task reminder notifications with deep links and action buttons.
 * Creates notifications that display task status counts and allow users to quickly
 * open the task list.
 *
 * Features:
 * - High-priority heads-up notifications
 * - Localized message with Chichewa greeting
 * - Dynamic weekly task counts
 * - Deep link to Tasks screen
 * - Proper notification grouping
 * - Compatible with Android API 26+
 */
object TaskReminderNotificationBuilder {
    // ===== WHATSAPP-STYLE GROUP CONSTANTS =====
    /**
     * Constant group key for all task notifications.
     * All child notifications must use this same group key.
     * The system will automatically group notifications with the same key.
     */
    const val GROUP_KEY_TASKS = "DHIS2_TASK_REMINDERS_GROUP"

    /**
     * Constant notification ID for the group summary notification.
     * This ID must be constant so the system can update/replace the same summary notification.
     * When posting, use this ID for the summary to enable proper grouping on Android 24+.
     */
    const val NOTIFICATION_GROUP_SUMMARY_ID = 1000

    /**
     * Start ID for child notifications (2000, 2001, 2002, ...).
     * Summary uses ID 1000, so children start from 2000 to avoid collision.
     * Up to 10 child notifications (IDs 2000-2009).
     */
    const val CHILD_NOTIFICATION_ID_START = 2000

    // Legacy constants (kept for compatibility)
    const val NOTIFICATION_ID = NOTIFICATION_GROUP_SUMMARY_ID
    const val TASKS_SCREEN_REQUEST_CODE = 1001
    const val TASKS_SCREEN_ACTION_REQUEST_CODE = 1002
    const val NOTIFICATION_GROUP_ID = 100

    /**
     * Build a group summary notification for WhatsApp-style grouped notifications.
     *
     * Summary Notification:
     * - Title: "You have X tasks this week"
     * - Body: Natural language summary of task counts
     * - Icon: Generic exclamation mark
     * - Click behavior: Opens TaskingFragment/Tasks tab
     * - Group ID: Constant (1000) for updating the same summary
     * - setGroup(GROUP_KEY_TASKS): Groups with all child notifications
     * - setGroupSummary(true): Marks this as the summary notification
     * - setGroupAlertBehavior(GROUP_ALERT_SUMMARY): Only summary makes sound/vibration
     *
     * Ensures:
     * - Valid icon resources for both notification and action
     * - Unique PendingIntent data URIs to avoid collision
     * - Proper grouping as summary notification
     * - Localized strings using resource strings
     * - Compatible PendingIntent flags
     * - Channel validation before building
     *
     * @param context Application context
     * @param counts Task counts by status
     * @return NotificationCompat.Builder configured for group summary
     */
    fun buildTaskReminderNotification(
        context: Context,
        counts: TaskStatusCounts
    ): NotificationCompat.Builder {
        return try {
            // Validate notification channel exists (critical for API 26+)
            NotificationChannelManager.createNotificationChannels(context)

            // Build notification text using natural language summary
            val contentText = buildNaturalLanguageSummary(counts)

            val title = context.getString(R.string.notification_title_weekly_summary)

            Timber.d("TaskReminderNotificationBuilder: Building summary notification - $contentText")

            // Create implicit intent to open Tasks using a deep link
            // The app module will need to handle the "app://tasks" deep link
            // See MainNavigator.MainScreen.TASKS navigation handling
            val deepLinkUri = "app://tasks/reminders?timestamp=${System.currentTimeMillis()}".toUri()
            val tasksIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // Create PendingIntent for notification tap
            // Use ACTION_VIEW with the unique URI to ensure proper uniqueness
            val contentPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    context,
                    TASKS_SCREEN_REQUEST_CODE,
                    tasksIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                PendingIntent.getActivity(
                    context,
                    TASKS_SCREEN_REQUEST_CODE,
                    tasksIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Create PendingIntent for "Open Tasks" action button with different request code
            val actionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    context,
                    TASKS_SCREEN_ACTION_REQUEST_CODE,
                    tasksIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                PendingIntent.getActivity(
                    context,
                    TASKS_SCREEN_ACTION_REQUEST_CODE,
                    tasksIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Calculate total task count for subtext
            val totalTasks = counts.open + counts.dueSoon + counts.dueToday + counts.overdue
            val subtext = if (totalTasks > 0) {
                "You have ${totalTasks} ${if (totalTasks == 1) "task" else "tasks"} this week"
            } else {
                "You have no pending tasks this week"
            }

            // Build expanded text showing breakdown of task counts
            val expandedText = buildString {
                append(contentText)
                append("\n\n")
                if (counts.overdue > 0) {
                    append("• ${counts.overdue} ${pluralize("overdue task", counts.overdue)}\n")
                }
                if (counts.dueToday > 0) {
                    append("• ${counts.dueToday} ${pluralize("task due today", counts.dueToday)}\n")
                }
                if (counts.dueSoon > 0) {
                    append("• ${counts.dueSoon} ${pluralize("task due soon", counts.dueSoon)}\n")
                }
                if (counts.open > 0) {
                    append("• ${counts.open} ${pluralize("open task", counts.open)}")
                }
            }

            // Build and return notification with proper icon and grouping
            NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                // Use exclamation mark icon for task reminder
                .setSmallIcon(R.drawable.ic_exclamation)
                // Set proper content
                .setContentTitle(title)
                .setContentText(contentText)
                // Add short subtext showing total count
                .setSubText(subtext)
                // Use BigTextStyle for expanded view showing task breakdown
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(expandedText)
                )
                // Set intent when notification is tapped
                .setContentIntent(contentPendingIntent)
                // Dismiss notification when user taps it
                .setAutoCancel(true)
                // High priority for heads-up behavior (API 25 and below)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Categorize as reminder
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                // Add action button with exclamation mark icon and unique request code
                .addAction(
                    R.drawable.ic_exclamation, // Exclamation mark icon for action
                    context.getString(R.string.notification_action_open_tasks),
                    actionPendingIntent
                )
                // ===== CRITICAL FOR WHATSAPP-STYLE GROUPING =====
                .setGroup(GROUP_KEY_TASKS)              // All notifications in same group
                .setGroupSummary(true)                  // This IS the group summary
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)  // Only summary makes sound

        } catch (e: Exception) {
            Timber.e(e, "TaskReminderNotificationBuilder: Error building summary notification")
            // Return a basic notification on error with fallback string
            try {
                NotificationChannelManager.createNotificationChannels(context)
                val fallbackText = context.getString(R.string.notification_error_fallback)
                val fallbackTitle = context.getString(R.string.notification_title_weekly_summary)

                NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_exclamation)
                    .setContentTitle(fallbackTitle)
                    .setContentText(fallbackText)
                    .setSubText(fallbackText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY_TASKS)
                    .setGroupSummary(true)
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "TaskReminderNotificationBuilder: Error building fallback notification")
                // Absolute fallback - minimum viable notification
                NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_exclamation)
                    .setContentTitle("Task Reminder")
                    .setContentText("You have pending tasks")
                    .setSubText("You have pending tasks")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup(GROUP_KEY_TASKS)
                    .setGroupSummary(true)
            }
        }
    }

    /**
     * Builds a natural language summary of task statuses for notification body.
     *
     * Examples:
     * - "You have 2 open tasks, 3 due soon tasks and 1 overdue task this week."
     * - "You have 1 overdue task this week."
     * - "You have no pending tasks this week."
     *
     * @param counts TaskStatusCounts with non-zero statuses
     * @return Natural language sentence summarizing task statuses
     */
    fun buildNaturalLanguageSummary(counts: TaskStatusCounts): String {
        val statuses = mutableListOf<String>()

        // Build individual status strings with proper singular/plural
        if (counts.open > 0) {
            statuses.add("${counts.open} ${pluralize("open task", counts.open)}")
        }
        if (counts.dueSoon > 0) {
            statuses.add("${counts.dueSoon} ${pluralize("due soon task", counts.dueSoon)}")
        }
        if (counts.dueToday > 0) {
            statuses.add("${counts.dueToday} ${pluralize("due today task", counts.dueToday)}")
        }
        if (counts.overdue > 0) {
            statuses.add("${counts.overdue} ${pluralize("overdue task", counts.overdue)}")
        }

        // Handle empty case
        if (statuses.isEmpty()) {
            return "You have no pending tasks this week."
        }

        // Join with commas and "and"
        val summary = when {
            statuses.size == 1 -> statuses[0]
            statuses.size == 2 -> "${statuses[0]} and ${statuses[1]}"
            else -> {
                // Multiple items: join all but last with commas, add "and" before last
                val withoutLast = statuses.dropLast(1).joinToString(", ")
                "$withoutLast and ${statuses.last()}"
            }
        }

        return "You have $summary this week."
    }

    /**
     * Build a child notification for a single task in the WhatsApp-style group.
     *
     * Child Notification:
     * - Title: Task name
     * - Body: "You have a {taskName} for {patientName} ({taskStatus}, Due {dueDate})"
     * - Icon: Program icon from the task (using .setLargeIcon())
     * - Click behavior: Opens TEI dashboard via deep link with onTaskClicked callback
     * - Action button: "Open Task" with same deep link
     * - Group: Same GROUP_KEY_TASKS for automatic grouping
     * - setGroupSummary(false): This is NOT the summary
     * - setGroupAlertBehavior(GROUP_ALERT_SUMMARY): Only summary makes sound
     *
     * Uses actual program icons and colors from the task (matching TaskingUiModel).
     *
     * @param context Application context
     * @param taskName Name of the task
     * @param patientName Name of the patient/TEI
     * @param taskStatus Status of the task (e.g., "Overdue", "Due Today", "Open")
     * @param sourceTeiUid UID of the TEI to navigate to
     * @param sourceProgramUid UID of the program
     * @param sourceEnrollmentUid UID of the enrollment
     * @param iconName Icon name from the task (e.g., "polio_outreach")
     * @param dueDate Due date string in format "yyyy-MM-dd" (e.g., "2025-08-25")
     * @param notificationId Unique ID for this notification (2000, 2001, ..., 2009)
     * @return NotificationCompat.Builder for the child notification
     */
    fun buildChildTaskNotification(
        context: Context,
        taskName: String,
        patientName: String,
        taskStatus: String,
        sourceTeiUid: String,
        sourceProgramUid: String,
        sourceEnrollmentUid: String,
        iconName: String?,
        dueDate: String?,
        notificationId: Int
    ): NotificationCompat.Builder {
        return try {
            NotificationChannelManager.createNotificationChannels(context)

            // Create intent for notification tap - opens TEI dashboard via deep link
            val deepLinkUri = "app://tei/$sourceTeiUid/$sourceProgramUid/$sourceEnrollmentUid".toUri()
            val teiIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                // Use NEW_TASK | CLEAR_TOP to create a proper back stack
                // This ensures back button returns to MainActivity (Tasks tab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val contentPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    teiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    teiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Create Activity intent for "Open Task" action button - same as notification tap
            val actionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    context,
                    notificationId + 1000,
                    teiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                PendingIntent.getActivity(
                    context,
                    notificationId + 1000,
                    teiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Format due date: "yyyy-MM-dd" to "dd/MM/yyyy"
            val formattedDueDate = if (!dueDate.isNullOrBlank()) {
                try {
                    val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
                    val date = inputFormat.parse(dueDate)
                    if (date != null) outputFormat.format(date) else dueDate
                } catch (e: Exception) {
                    dueDate
                }
            } else {
                "Unknown"
            }

            // Format: "You have a {taskName} for {patientName} ({taskStatus}, Due {formattedDueDate})"
            val contentText = "You have a $taskName for $patientName ($taskStatus, Due $formattedDueDate)"

            // Determine icon resource - matching TaskingUiModel logic
            // If iconName exists, use "dhis2_" + iconName, otherwise use "dhis2_default"
            val iconResName = iconName?.takeIf { it.isNotBlank() }?.let { "dhis2_$it" } ?: "dhis2_default"

            // Get icon resource ID - fallback to exclamation mark if not found
            val iconResId = try {
                context.resources.getIdentifier(iconResName, "drawable", context.packageName)
            } catch (e: Exception) {
                0
            }

            val finalIconResId = if (iconResId != 0) iconResId else R.drawable.ic_exclamation

            NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                // Use program icon from task (matching TaskingUiModel)
                .setSmallIcon(finalIconResId)
                .setContentTitle(taskName)
                .setContentText(contentText)
                // NO SUBTEXT on child notifications - subtext only on group summary
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(contentText)
                )
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                // Add action button "Open Task" with deep link to TEI
                .addAction(
                    finalIconResId,
                    context.getString(R.string.notification_action_open_tasks),
                    actionPendingIntent
                )
                // ===== CRITICAL FOR WHATSAPP-STYLE GROUPING =====
                .setGroup(GROUP_KEY_TASKS)           // Same group as summary
                .setGroupSummary(false)              // This is NOT the summary
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)  // Only summary makes sound

        } catch (e: Exception) {
            Timber.e(e, "TaskReminderNotificationBuilder: Error building child notification")
            // Return a basic notification on error with exclamation mark
            NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_exclamation)
                .setContentTitle(taskName)
                .setContentText("You have a task for $patientName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_KEY_TASKS)
                .setGroupSummary(false)
        }
    }

    /**
     * Helper function to pluralize task status labels for natural language summary.
     * Used in buildNaturalLanguageSummary() to format grammatically correct text.
     *
     * Examples:
     * - "1 open task" (singular)
     * - "2 open tasks" (plural)
     *
     * @param label The task status label (e.g., "open task", "overdue task")
     * @param count The count to determine singular/plural
     * @return Pluralized label (e.g., "tasks" if count != 1, original label if count == 1)
     */
    private fun pluralize(label: String, count: Int): String {
        return if (count == 1) {
            label // Singular: "1 open task"
        } else {
            label.replace("task", "tasks") // Plural: "2 open tasks"
        }
    }
}
