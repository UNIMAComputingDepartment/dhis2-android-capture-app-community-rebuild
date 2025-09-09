package org.dhis2.community.tasking.filters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskFilterRepository @Inject constructor() {
    private val _selectedFilters = MutableStateFlow(TaskFilter())
    val selectedFilters: StateFlow<TaskFilter> = _selectedFilters

    fun updateFilter(filter: TaskFilter) {
        _selectedFilters.value = filter
    }

    fun clearFilters() {
        _selectedFilters.value = TaskFilter()
    }

    fun filterTasks(tasks: List<org.dhis2.community.tasking.ui.TaskingUiModel>): List<org.dhis2.community.tasking.ui.TaskingUiModel> {
        val filter = _selectedFilters.value
        return tasks.filter { uiModel ->
            val task = uiModel.task
            // Program filter
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            // OrgUnit filter (use correct orgUnit field)
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(uiModel.orgUnit)) &&
            // Priority filter
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            // Status filter
            (filter.statusFilters.isEmpty() || filter.statusFilters.contains(task.status)) &&
            // Due date filter
            (filter.dueDateRange == null || matchesDueDateFilter(uiModel, filter.dueDateRange, filter.customDateRange))
        }
    }

    private fun matchesDueDateFilter(
        uiModel: org.dhis2.community.tasking.ui.TaskingUiModel,
        dateRange: org.dhis2.community.tasking.filters.models.DateRangeFilter?,
        customRange: org.dhis2.community.tasking.filters.models.CustomDateRange?
    ): Boolean {
        val dueDate = uiModel.dueDate ?: return false
        val today = java.util.Calendar.getInstance()
        val calDue = java.util.Calendar.getInstance().apply { time = dueDate }
        return when (dateRange) {
            org.dhis2.community.tasking.filters.models.DateRangeFilter.Today -> calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && calDue.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
            org.dhis2.community.tasking.filters.models.DateRangeFilter.Yesterday -> {
                today.add(java.util.Calendar.DAY_OF_YEAR, -1)
                calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && calDue.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
            }
            org.dhis2.community.tasking.filters.models.DateRangeFilter.ThisWeek -> calDue.get(java.util.Calendar.WEEK_OF_YEAR) == today.get(java.util.Calendar.WEEK_OF_YEAR) && calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
            org.dhis2.community.tasking.filters.models.DateRangeFilter.LastWeek -> {
                today.add(java.util.Calendar.WEEK_OF_YEAR, -1)
                calDue.get(java.util.Calendar.WEEK_OF_YEAR) == today.get(java.util.Calendar.WEEK_OF_YEAR) && calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
            }
            org.dhis2.community.tasking.filters.models.DateRangeFilter.ThisMonth -> calDue.get(java.util.Calendar.MONTH) == today.get(java.util.Calendar.MONTH) && calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
            org.dhis2.community.tasking.filters.models.DateRangeFilter.LastMonth -> {
                today.add(java.util.Calendar.MONTH, -1)
                calDue.get(java.util.Calendar.MONTH) == today.get(java.util.Calendar.MONTH) && calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
            }
            org.dhis2.community.tasking.filters.models.DateRangeFilter.Custom -> {
                val from = customRange?.from
                val to = customRange?.to
                (from == null || !dueDate.before(from)) && (to == null || !dueDate.after(to))
            }
            else -> true
        }
    }
}
