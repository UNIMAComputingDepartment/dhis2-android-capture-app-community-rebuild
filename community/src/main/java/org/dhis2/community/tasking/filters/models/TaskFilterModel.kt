package org.dhis2.community.tasking.filters.models

import android.util.Log
import java.util.Date

data class TaskFilterModel(
    val programFilters: Set<String> = emptySet(),
    val orgUnitFilters: Set<String> = emptySet(),
    val priorityFilters: Set<org.dhis2.community.tasking.ui.TaskingPriority> = emptySet(),
    val statusFilters: Set<org.dhis2.community.tasking.ui.TaskingStatus> = emptySet(),
    val dueDateRange: DateRangeFilter? = null
) {
    init {
        Log.d("TaskFilterModel", "TaskFilterModel created: $this")
    }
}

enum class DateRangeFilter {
    Today,
    Yesterday,
    Tomorrow,
    ThisWeek,
    LastWeek,
    NextWeek,
    ThisMonth,
    LastMonth,
    NextMonth,
}

data class FilterUiState(
    val isProgramFilterActive: Boolean = false,
    val programFilterCount: Int = 0,
    val isOrgUnitFilterActive: Boolean = false,
    val orgUnitFilterCount: Int = 0,
    val isPriorityFilterActive: Boolean = false,
    val priorityFilterCount: Int = 0,
    val isStatusFilterActive: Boolean = false,
    val statusFilterCount: Int = 0,
    val isDueDateFilterActive: Boolean = false,
    val dueDateFilterCount: Int = 0,
    val selectedDateRange: DateRangeFilter? = null,
    val selectedOrgUnits: List<String> = listOf()
) {
}
