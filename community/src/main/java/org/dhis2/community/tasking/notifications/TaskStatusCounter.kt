package org.dhis2.community.tasking.notifications

import org.dhis2.community.tasking.services.TaskCountService
import org.hisp.dhis.android.core.D2
import timber.log.Timber

data class TaskStatusCounts(
    val open: Int = 0,
    val dueSoon: Int = 0,
    val dueToday: Int = 0,
    val overdue: Int = 0
)

class TaskStatusCounter(private val d2: D2) {
    fun getTaskStatusCounts(): TaskStatusCounts {
        return try {
            Timber.d("TaskStatusCounter: Fetching task status counts")
            val countService = TaskCountService(d2)
            val counts = countService.getTaskStatusCounts()
            Timber.d("TaskStatusCounter: Retrieved counts - Open: ${counts.open}, DueSoon: ${counts.dueSoon}, DueToday: ${counts.dueToday}, Overdue: ${counts.overdue}")
            counts
        } catch (e: Exception) {
            Timber.e(e, "TaskStatusCounter: Error getting task status counts")
            TaskStatusCounts()
        }
    }
}


