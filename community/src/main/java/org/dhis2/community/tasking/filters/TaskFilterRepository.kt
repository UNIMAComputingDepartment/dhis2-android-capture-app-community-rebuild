package org.dhis2.community.tasking.filters

import android.util.Log
import io.reactivex.Flowable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import org.dhis2.community.tasking.filters.models.TaskFilterModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskFilterRepository @Inject constructor() {
    init {
        Log.d("TaskFilterRepository", "TaskFilterRepository initialized")
    }

    private val _selectedFilters = MutableStateFlow(TaskFilter())
    val selectedFilters: StateFlow<TaskFilter> = _selectedFilters

    private val _selectedFiltersRx = PublishProcessor.create<TaskFilterModel>()
    val selectedFiltersRx: FlowableProcessor<TaskFilterModel> = _selectedFiltersRx
    private var currentFilters = TaskFilterModel()

    fun onResumeFilters() {
        Log.d("TaskFilterRepository", "onResumeFilters called")
        _selectedFiltersRx.onNext(currentFilters)
    }

    fun updateFilter(filter: TaskFilter) {
        Log.d("TaskFilterRepository", "updateFilter called with: $filter")
        _selectedFilters.value = filter
    }

    fun updateFilter(filters: TaskFilterModel) {
        Log.d("TaskFilterRepository", "Updating filters: $filters")
        currentFilters = filters
        _selectedFiltersRx.onNext(filters)
    }

    fun clearFilters() {
        Log.d("TaskFilterRepository", "clearFilters called, resetting filters to default")
        _selectedFilters.value = TaskFilter()
        Log.d("TaskFilterRepository", "Clearing all filters")
        currentFilters = TaskFilterModel()
        _selectedFiltersRx.onNext(currentFilters)
    }

    fun filterTasks(tasks: List<org.dhis2.community.tasking.ui.TaskingUiModel>): List<org.dhis2.community.tasking.ui.TaskingUiModel> {
        val filter = _selectedFilters.value
        Log.d("TaskFilterRepository", "filterTasks called with ${tasks.size} tasks and filter: $filter")
        val filtered = tasks.filter { uiModel ->
            val task = uiModel.task
            // Program filter
            (filter.programFilters.isEmpty() || filter.programFilters.contains(uiModel.sourceProgramUid)) &&
                    // OrgUnit filter (use correct orgUnit field)
                    (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(uiModel.orgUnit)) &&
                    // Priority filter
                    (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
                    // Status filter
                    (filter.statusFilters.isEmpty() || filter.statusFilters.contains(task.status)) &&
                    // Due date filter
                    (filter.dueDateRange == null || matchesDueDateFilter(uiModel, filter.dueDateRange))
        }
        Log.d("TaskFilterRepository", "filterTasks result: ${filtered.size} tasks after filtering")
        return filtered
    }

    private fun matchesDueDateFilter(
        uiModel: org.dhis2.community.tasking.ui.TaskingUiModel,
        dateRange: org.dhis2.community.tasking.filters.models.DateRangeFilter?
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
            else -> true
        }
    }
}
