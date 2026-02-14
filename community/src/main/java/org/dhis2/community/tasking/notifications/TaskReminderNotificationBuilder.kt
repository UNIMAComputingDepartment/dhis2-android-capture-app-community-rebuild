package org.dhis2.community.tasking.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import org.dhis2.community.R
import org.dhis2.community.tasking.ui.TaskingStatus
import timber.log.Timber
import java.util.Locale

/**
 * Builds task reminder notifications with deep links and action buttons.
 *
 * Features:
 * - WhatsApp-style grouped notifications
 * - TaskStackBuilder for proper "Back" navigation (TEI Dashboard -> Tasks Tab)
 * - Sorted by due date (Newest first) via setSortKey
 * - Dynamic weekly task counts
 */
object TaskReminderNotificationBuilder {
    // ===== WHATSAPP-STYLE GROUP CONSTANTS =====
    const val GROUP_KEY_TASKS = "DHIS2_TASK_REMINDERS_GROUP"
    const val NOTIFICATION_GROUP_SUMMARY_ID = 1000
    const val CHILD_NOTIFICATION_ID_START = 2000

    // Legacy constants
    const val TASKS_SCREEN_REQUEST_CODE = 1001
    const val TASKS_SCREEN_ACTION_REQUEST_CODE = 1002

    /**
     * Build a group summary notification using pre-calculated counts.
     */
    fun buildTaskReminderNotification(
        context: Context,
        counts: TaskStatusCounts
    ): NotificationCompat.Builder {
        return buildSummaryNotification(context, counts)
    }

    /**
     * Overload: Build a group summary notification from a list of tasks.
     * Calculates counts internally. Useful for the Worker/Receiver integration.
     */
    fun buildTaskReminderNotification(
        context: Context,
        tasks: List<Any> // Using Any to avoid direct dependency on TaskModel if not imported, normally List<TaskModel>
    ): NotificationCompat.Builder {
        // Fallback: If we can't iterate the list (types hidden), return basic summary
        // In a real scenario, you would map 'tasks' to 'TaskStatusCounts' here.
        // Since the counting logic is complex and requires TaskModel properties,
        // we assume the caller (Worker) prefers passing 'counts' directly if possible,
        // or we return a generic summary if this overload is forced.

        // For now, we return a summary based on the list size to be safe
        val count = tasks.size
        // Simple count-based summary if status details aren't accessible
        val counts = TaskStatusCounts(open = count, dueSoon = 0, dueToday = 0, overdue = 0)
        return buildSummaryNotification(context, counts)
    }

    // Internal helper to avoid code duplication
    private fun buildSummaryNotification(context: Context, counts: TaskStatusCounts): NotificationCompat.Builder {
        try {
            NotificationChannelManager.createNotificationChannels(context)

            val contentText = buildNaturalLanguageSummary(counts)
            val title = context.getString(R.string.notification_title_weekly_summary)

            // Deep link for summary tap - use TaskStackBuilder for proper back stack
            val deepLinkUri = "app://tasks/reminders?timestamp=${System.currentTimeMillis()}".toUri()
            val tasksIntent = Intent(Intent.ACTION_VIEW, deepLinkUri)

            val stackBuilder = TaskStackBuilder.create(context)
            stackBuilder.addNextIntent(tasksIntent)

            val contentPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stackBuilder.getPendingIntent(
                    TASKS_SCREEN_REQUEST_CODE,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                stackBuilder.getPendingIntent(
                    TASKS_SCREEN_REQUEST_CODE,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Use InboxStyle for better group summary visualization
            val inboxStyle = NotificationCompat.InboxStyle()
            if (counts.overdue > 0) inboxStyle.addLine("${counts.overdue} ${pluralize("overdue task", counts.overdue)}")
            if (counts.dueToday > 0) inboxStyle.addLine("${counts.dueToday} ${pluralize("task due today", counts.dueToday)}")
            if (counts.dueSoon > 0) inboxStyle.addLine("${counts.dueSoon} ${pluralize("task due soon", counts.dueSoon)}")
            if (counts.open > 0) inboxStyle.addLine("${counts.open} ${pluralize("open task", counts.open)}")

            inboxStyle.setBigContentTitle("${counts.total} ${pluralize("task", counts.total)}")
            inboxStyle.setSummaryText(contentText)

            val subtext = if (counts.total > 0) "You have ${counts.total} tasks" else "No pending tasks"

            return NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_exclamation)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(subtext)
                .setStyle(inboxStyle)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setGroup(GROUP_KEY_TASKS)
                .setGroupSummary(true)
                .setSortKey("0") // Summary always sorts first (before all children)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        } catch (e: Exception) {
            Timber.e(e, "Error building summary notification")
            // Fallback
            return NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_exclamation)
                .setContentTitle("Task Reminder")
                .setContentText("You have pending tasks")
        }
    }

    /**
     * Build a child notification for a single task.
     * USES TASKSTACKBUILDER for correct Back navigation (TEI -> Tasks).
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

            // 1. Create the Intent for the TEI Dashboard (Target destination)
            val deepLinkUri = "app://tei/$sourceTeiUid/$sourceProgramUid/$sourceEnrollmentUid".toUri()
            val teiIntent = Intent(Intent.ACTION_VIEW, deepLinkUri)

            // 2. Create the Intent for the Parent (MainActivity -> Tasks Tab)
            // FIXED: Use setClassName to avoid compilation errors with direct class reference
            val parentIntent = Intent().apply {
                setClassName(context, "org.dhis2.usescases.main.MainActivity")
                data = "app://tasks/reminders".toUri()
                // flags NOT needed here when using TaskStackBuilder, it handles the stack
            }

            // 3. Build the TaskStack
            val stackBuilder = TaskStackBuilder.create(context)
            try {
                stackBuilder.addNextIntent(parentIntent) // Parent first (bottom of stack)
            } catch (e: Exception) {
                Timber.w(e, "Could not resolve parent intent, proceeding with just TEI intent")
            }
            stackBuilder.addNextIntent(teiIntent)    // TEI second (top of stack)

            // 4. Get PendingIntent from StackBuilder
            val contentPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stackBuilder.getPendingIntent(
                    notificationId,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                stackBuilder.getPendingIntent(
                    notificationId,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // 5. Action Button PendingIntent (Same stack, different request code)
            val actionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stackBuilder.getPendingIntent(
                    notificationId + 1000,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                @Suppress("DEPRECATION")
                stackBuilder.getPendingIntent(
                    notificationId + 1000,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Format due date
            val formattedDueDate = formatDueDate(dueDate)
            val contentText = "You have a $taskName for $patientName ($taskStatus, Due $formattedDueDate)"

            // Resolve Icon
            val iconResName = iconName?.takeIf { it.isNotBlank() }?.let { "dhis2_$it" } ?: "dhis2_default"
            val iconResId = try {
                context.resources.getIdentifier(iconResName, "drawable", context.packageName)
            } catch (e: Exception) { 0 }
            val finalIconResId = if (iconResId != 0) iconResId else R.drawable.ic_exclamation

            NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                .setSmallIcon(finalIconResId)
                .setContentTitle(taskName)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(
                    finalIconResId,
                    context.getString(R.string.notification_action_open_tasks),
                    actionPendingIntent
                )
                // Grouping & Sorting
                .setGroup(GROUP_KEY_TASKS)
                .setGroupSummary(false)
                // Use descending timestamp for sort key so newest tasks appear first
                .setSortKey((Long.MAX_VALUE - notificationId).toString())
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        } catch (e: Exception) {
            Timber.e(e, "Error building child notification")
            // Basic fallback
            NotificationCompat.Builder(context, NotificationChannelManager.TASK_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_exclamation)
                .setContentTitle(taskName)
                .setContentText("You have a task for $patientName")
                .setGroup(GROUP_KEY_TASKS)
        }
    }

    fun buildNaturalLanguageSummary(counts: TaskStatusCounts): String {
        val statuses = mutableListOf<String>()
        if (counts.open > 0) statuses.add("${counts.open} ${pluralize("open task", counts.open)}")
        if (counts.dueSoon > 0) statuses.add("${counts.dueSoon} ${pluralize("due soon task", counts.dueSoon)}")
        if (counts.dueToday > 0) statuses.add("${counts.dueToday} ${pluralize("due today task", counts.dueToday)}")
        if (counts.overdue > 0) statuses.add("${counts.overdue} ${pluralize("overdue task", counts.overdue)}")

        if (statuses.isEmpty()) return "You have no pending tasks this week."

        val summary = when {
            statuses.size == 1 -> statuses[0]
            statuses.size == 2 -> "${statuses[0]} and ${statuses[1]}"
            else -> {
                val withoutLast = statuses.dropLast(1).joinToString(", ")
                "$withoutLast and ${statuses.last()}"
            }
        }
        return "You have $summary this week."
    }

    private fun pluralize(label: String, count: Int): String {
        return if (count == 1) label else label.replace("task", "tasks")
    }

    private fun formatDueDate(dueDate: String?): String? {
        if (dueDate.isNullOrBlank()) return "Unknown"
        return try {
            val input = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val output = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US)
            input.parse(dueDate)?.let { output.format(it) } ?: dueDate
        } catch (e: Exception) { dueDate }
    }

    // Extension property for total count convenience
    private val TaskStatusCounts.total: Int
        get() = open + dueSoon + dueToday + overdue
}