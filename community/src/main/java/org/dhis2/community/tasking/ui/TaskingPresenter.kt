package org.dhis2.community.tasking.ui

import androidx.compose.runtime.mutableStateListOf
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import org.dhis2.community.tasking.filters.models.TaskFilterModel

class TaskingPresenter @Inject constructor(
    private val filterRepository: TaskFilterRepository,
    private val filterManager: FilterManager
) {
    private val _tasks = mutableStateListOf<TaskingUiModel>()
    val tasks: List<TaskingUiModel> get() = _tasks
    private val disposable = CompositeDisposable()

    fun observeFilters(tasksFromRepo: List<TaskingUiModel>) {
        disposable.add(
            filterManager.asFlowable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val taskFilter = filterRepository.selectedFilters.value
                    val filterModel = TaskFilterModel(
                        programFilters = taskFilter.programFilters,
                        orgUnitFilters = taskFilter.orgUnitFilters,
                        priorityFilters = taskFilter.priorityFilters.mapNotNull { label ->
                            TaskingPriority.entries.find { it.label == label }
                        }.toSet(),
                        statusFilters = taskFilter.statusFilters.mapNotNull { label ->
                            TaskingStatus.entries.find { it.label == label }
                        }.toSet(),
                        dueDateRange = taskFilter.dueDateRange,
                        customDateRange = taskFilter.customDateRange
                    )
                    val filtered = applyTaskFilters(tasksFromRepo, filterModel)
                    updateFilteredTasks(filtered)
                }
        )
    }

    fun initialize(tasksFromRepo: List<TaskingUiModel>) {
        updateFilteredTasks(tasksFromRepo)
    }

    fun onResume() {
        // Add any logic needed for resuming presenter
    }

    private fun applyTaskFilters(tasks: List<TaskingUiModel>, filter: TaskFilterModel): List<TaskingUiModel> {
        return tasks.filter { task ->
            val dueDate = task.dueDate
            // Program filter
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            // OrgUnit filter
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.teiSecondary)) &&
            // Priority filter
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            // Status filter
            (filter.statusFilters.isEmpty() || filter.statusFilters.contains(task.status)) &&
            // Due date filter
            (filter.dueDateRange == null || matchesDueDateFilter(dueDate, filter.dueDateRange, filter.customDateRange))
        }
    }

    private fun matchesDueDateFilter(
        dueDate: java.util.Date?,
        dateRange: org.dhis2.community.tasking.filters.models.DateRangeFilter?,
        customRange: org.dhis2.community.tasking.filters.models.CustomDateRange?
    ): Boolean {
        if (dueDate == null) return false
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

    private fun updateFilteredTasks(filtered: List<TaskingUiModel>) {
        _tasks.clear()
        _tasks.addAll(filtered)
    }

    fun clear() {
        disposable.clear()
    }
}
