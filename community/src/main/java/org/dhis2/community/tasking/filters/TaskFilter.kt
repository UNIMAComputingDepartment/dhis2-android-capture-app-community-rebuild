package org.dhis2.community.tasking.filters

import android.util.Log

data class TaskFilter(
    val programFilters: Set<String> = emptySet(),
    val orgUnitFilters: Set<String> = emptySet(),
    val priorityFilters: Set<String> = emptySet(),
    val statusFilters: Set<String> = emptySet(),
    val dueDateRange: org.dhis2.community.tasking.filters.models.DateRangeFilter? = null
) {
    init {
        Log.d("TaskFilter", "TaskFilter created: $this")
    }
    fun isEmpty(): Boolean {
        val empty = programFilters.isEmpty() && orgUnitFilters.isEmpty() &&
                priorityFilters.isEmpty() && statusFilters.isEmpty() && dueDateRange == null
        Log.d("TaskFilter", "TaskFilter isEmpty called: $empty for $this")
        return empty
    }
}
