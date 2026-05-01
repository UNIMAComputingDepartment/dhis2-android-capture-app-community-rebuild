package org.dhis2.community.tasking.services

import org.dhis2.community.tasking.notifications.TaskStatusCounts
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight service for counting tasks by status without loading full task details.
 *
 * Used by notification system to avoid expensive full task queries.
 * Implements simple TTL-based caching to reduce database load.
 */
class TaskCountService(private val d2: D2) {
    private val countCache = AtomicReference<CachedCounts?>(null)
    private val cacheTimestamp = AtomicLong(0)
    private val CACHE_TTL_MILLIS = 5 * 60 * 1000L // 5 minutes

    data class CachedCounts(
        val counts: TaskStatusCounts,
        val timestamp: Long
    )

    /**
     * Get task status counts with caching.
     * Cache is invalidated after 5 minutes or on explicit call.
     */
    fun getTaskStatusCounts(forceRefresh: Boolean = false): TaskStatusCounts {
        val now = System.currentTimeMillis()
        val cached = countCache.get()

        // Return cached if fresh and not forced refresh
        if (!forceRefresh && cached != null && (now - cached.timestamp) < CACHE_TTL_MILLIS) {
            Timber.d("TaskCountService: Returning cached counts (${now - cached.timestamp}ms old)")
            return cached.counts
        }

        Timber.d("TaskCountService: Computing fresh task status counts (forceRefresh=$forceRefresh)")

        return try {
            val repository = TaskingRepository(d2)

            // Get all tasks - this is the expensive operation
            val allTasks = repository.getAllTasks()

            if (allTasks.isEmpty()) {
                Timber.w("TaskCountService: No tasks found in repository")
                val emptyCounts = TaskStatusCounts()
                cacheResult(emptyCounts, now)
                return emptyCounts
            }

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val weekStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                val daysToMonday = if (dayOfWeek == Calendar.SUNDAY) 1 else dayOfWeek - Calendar.MONDAY
                add(Calendar.DAY_OF_MONTH, -daysToMonday)
            }

            val weekEnd = Calendar.getInstance().apply {
                time = weekStart.time
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

            for (task in allTasks) {
                val taskStatus = task.status.trim().lowercase()
                if (taskStatus == "completed" || taskStatus == "defaulted") {
                    continue
                }

                val dueDateString = task.dueDate
                if (dueDateString.isBlank() || dueDateString.matches(Regex("[a-zA-Z].*"))) {
                    continue
                }

                val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
                if (!dateRegex.matches(dueDateString)) {
                    continue
                }

                try {
                    val dueDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dueDateString) ?: continue
                    val dueCal = Calendar.getInstance().apply {
                        time = dueDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    when {
                        dueCal.before(today) -> overdueCount++
                        dueCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                dueCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> dueTodayCount++
                        dueCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                dueCal.get(Calendar.DAY_OF_YEAR) in (today.get(Calendar.DAY_OF_YEAR) + 1)..(today.get(Calendar.DAY_OF_YEAR) + 3) -> dueSoonCount++
                        dueCal.before(weekEnd) || dueCal.equals(today.time) -> openCount++
                    }
                } catch (e: Exception) {
                    Timber.w(e, "TaskCountService: Error parsing due date: $dueDateString")
                }
            }

            val counts = TaskStatusCounts(
                open = openCount,
                dueSoon = dueSoonCount,
                dueToday = dueTodayCount,
                overdue = overdueCount
            )

            Timber.d("TaskCountService: Computed counts - Open: $openCount, DueSoon: $dueSoonCount, DueToday: $dueTodayCount, Overdue: $overdueCount")

            cacheResult(counts, now)
            counts
        } catch (e: Exception) {
            Timber.e(e, "TaskCountService: Error computing task status counts")
            TaskStatusCounts()
        }
    }

    private fun cacheResult(counts: TaskStatusCounts, timestamp: Long) {
        countCache.set(CachedCounts(counts, timestamp))
        cacheTimestamp.set(timestamp)
        Timber.d("TaskCountService: Cached counts with timestamp $timestamp")
    }

    /**
     * Invalidate cache to force refresh on next getTaskStatusCounts() call.
     * Called by notification system when tasks are created/updated.
     */
    @Suppress("unused")
    fun invalidateCache() {
        countCache.set(null)
        cacheTimestamp.set(0)
        Timber.d("TaskCountService: Cache invalidated")
    }
}
