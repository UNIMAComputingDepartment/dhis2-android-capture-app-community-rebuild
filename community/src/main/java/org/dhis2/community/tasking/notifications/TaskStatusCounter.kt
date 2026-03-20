package org.dhis2.community.tasking.notifications

import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber
import java.util.Calendar

data class TaskStatusCounts(
    val open: Int = 0,
    val dueSoon: Int = 0,
    val dueToday: Int = 0,
    val overdue: Int = 0
)
class TaskStatusCounter(private val d2: D2) {
    fun getTaskStatusCounts(): TaskStatusCounts {
        return try {
            val repository = TaskingRepository(d2)

            // Get all tasks from repository
            val allTasks = repository.getAllTasks()

            if (allTasks.isEmpty()) {
                Timber.w("TaskStatusCounter: WARNING - No tasks found in repository!")
                return TaskStatusCounts()
            }

            // Get current date and calculate week boundaries (matching TaskingViewModel logic)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Calculate week boundaries (Monday to Sunday) - MUST MATCH TaskingViewModel
            val weekStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Set to Monday of current week
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                val daysToMonday = if (dayOfWeek == Calendar.SUNDAY) 1 else dayOfWeek - Calendar.MONDAY
                add(Calendar.DAY_OF_MONTH, -daysToMonday)
            }

            val weekEnd = Calendar.getInstance().apply {
                time = weekStart.time
                // Add 6 days to get to Sunday
                add(Calendar.DAY_OF_MONTH, 6)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            var openCount = 0
            var dueSoonCount = 0
            var dueTodayCount = 0
            var overdueCount = 0
            var skippedCount = 0
            var outOfWeekCount = 0

            for ((index, task) in allTasks.withIndex()) {
                Timber.d("TaskStatusCounter: ")
                Timber.d("TaskStatusCounter: [Task ${index + 1}/${allTasks.size}] ${task.name}")
                Timber.d("TaskStatusCounter: ├─ Status: '${task.status}' | DueDate: '${task.dueDate}'")

                val taskStatus = task.status.trim().lowercase()
                if (taskStatus == "completed" || taskStatus == "defaulted") {
                    skippedCount++
                    continue
                }

                // Parse due date using same logic as TaskingUiModel
                val dueDateString = task.dueDate
                if (dueDateString.isBlank()) {
                    skippedCount++
                    continue
                }

                // Check if due date looks valid
                if (dueDateString.matches(Regex("[a-zA-Z].*"))) {
                    skippedCount++
                    continue
                }

                val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
                if (!dateRegex.matches(dueDateString)) {
                    skippedCount++
                    continue
                }

                try {
                    val dueDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dueDateString)
                        ?: run {
                            skippedCount++
                            return@run null
                        }

                    if (dueDate == null) continue

                    val dueDateCalendar = Calendar.getInstance().apply {
                        time = dueDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    // Use timeInMillis for proper comparison
                    val taskTimeMillis = dueDateCalendar.timeInMillis
                    val weekStartMillis = weekStart.timeInMillis
                    val weekEndMillis = weekEnd.timeInMillis

                    Timber.d("TaskStatusCounter: ├─ TaskTime: ${dueDateCalendar.time} (${taskTimeMillis}ms)")
                    Timber.d("TaskStatusCounter: ├─ WeekStart: ${weekStart.time} (${weekStartMillis}ms)")
                    Timber.d("TaskStatusCounter: ├─ WeekEnd: ${weekEnd.time} (${weekEndMillis}ms)")

                    // Only count tasks within this week
                    if (taskTimeMillis >= weekStartMillis && taskTimeMillis <= weekEndMillis) {
                        Timber.d("TaskStatusCounter: ├─ ✓ Within week - categorizing...")

                        // Use same categorization logic as TaskingUiModel.calculateStatus()
                        when {
                            // Overdue: due date is before today
                            dueDateCalendar.before(today) -> {
                                overdueCount++
                                Timber.d("TaskStatusCounter: ├─ ✓ Counted as OVERDUE (${overdueCount})")
                            }
                            // Due Today: same date as today
                            dueDateCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            dueDateCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
                                dueTodayCount++
                                Timber.d("TaskStatusCounter: ├─ ✓ Counted as DUE_TODAY (${dueTodayCount})")
                            }
                            // Due Soon: within next 3 days (tomorrow, +2, +3)
                            dueDateCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            dueDateCalendar.get(Calendar.DAY_OF_YEAR) in (today.get(Calendar.DAY_OF_YEAR) + 1)..(today.get(Calendar.DAY_OF_YEAR) + 3) -> {
                                dueSoonCount++
                                Timber.d("TaskStatusCounter: ├─ ✓ Counted as DUE_SOON (${dueSoonCount})")
                            }
                            // Open: due in this week but more than 3 days away
                            else -> {
                                openCount++
                                Timber.d("TaskStatusCounter: ├─ ✓ Counted as OPEN (${openCount})")
                            }
                        }
                    } else {
                        outOfWeekCount++
                    }
                } catch (e: Exception) {
                    skippedCount++
                }
            }

            TaskStatusCounts(
                open = openCount,
                dueSoon = dueSoonCount,
                dueToday = dueTodayCount,
                overdue = overdueCount
            )
        } catch (e: Exception) {
            Timber.e(e, "TaskStatusCounter: FATAL ERROR - getTaskStatusCounts failed")
            TaskStatusCounts()
        }
    }
}
