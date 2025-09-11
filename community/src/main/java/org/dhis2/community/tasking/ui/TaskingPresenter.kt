package org.dhis2.community.tasking.ui

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.dhis2.community.tasking.filters.models.TaskFilterModel
import javax.inject.Inject
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit

class TaskingPresenter @Inject constructor(
    private val filterRepository: TaskFilterRepository,
    private val filterManager: FilterManager
) {
    private val _tasks = mutableStateListOf<TaskingUiModel>()
    val tasks: List<TaskingUiModel> get() = _tasks
    private val disposable = CompositeDisposable()

    init {
        Log.d("TaskingPresenter", "TaskingPresenter initialized")
    }

    fun observeFilters(tasksFromRepo: List<TaskingUiModel>) {
        Log.d("TaskingPresenter", "observeFilters called with ${tasksFromRepo.size} tasks")
        disposable.clear() // Ensure no duplicate subscriptions
        disposable.add(
            filterManager.asFlowable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Log.d("TaskingPresenter", "FilterManager emitted new filter state")
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
                    )
                    Log.d("TaskingPresenter", "Applying filters: $filterModel")
                    val filtered = applyTaskFilters(tasksFromRepo, filterModel)
                    Log.d("TaskingPresenter", "Filtered tasks count: ${filtered.size}")
                    updateFilteredTasks(filtered)
                }
        )
    }

    fun initialize(tasksFromRepo: List<TaskingUiModel>) {
        Log.d("TaskingPresenter", "initialize called with ${tasksFromRepo.size} tasks")
        updateFilteredTasks(tasksFromRepo)
    }

    fun onResume() {
        Log.d("TaskingPresenter", "TaskingPresenter onResume called")
        // Add any logic needed for resuming presenter
    }

    private fun applyTaskFilters(tasks: List<TaskingUiModel>, filter: TaskFilterModel): List<TaskingUiModel> {
        Log.d("TaskingPresenter", "applyTaskFilters called with ${tasks.size} tasks and filter: $filter")
        return tasks.filter { task ->
            val dueDate = task.dueDate
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            (filter.statusFilters.isEmpty() || filter.statusFilters.any { it.label == task.status.label }) &&
            (filter.dueDateRange == null || matchesDueDateFilter(dueDate, filter.dueDateRange))
        }
    }

    private fun matchesDueDateFilter(
        dueDate: java.util.Date?,
        dateRange: org.dhis2.community.tasking.filters.models.DateRangeFilter?,
    ): Boolean {
        Log.d("TaskingPresenter", "matchesDueDateFilter called with dueDate: $dueDate, dateRange: $dateRange")
        if (dueDate == null) return false
        val today = java.util.Calendar.getInstance()
        val calDue = java.util.Calendar.getInstance().apply { time = dueDate }
        return when (dateRange) {
            org.dhis2.community.tasking.filters.models.DateRangeFilter.Today -> calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && calDue.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
            org.dhis2.community.tasking.filters.models.DateRangeFilter.Yesterday -> {
                val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                calDue.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) && calDue.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
            }
            org.dhis2.community.tasking.filters.models.DateRangeFilter.ThisWeek -> calDue.get(java.util.Calendar.WEEK_OF_YEAR) == today.get(java.util.Calendar.WEEK_OF_YEAR) && calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
            org.dhis2.community.tasking.filters.models.DateRangeFilter.LastWeek -> {
                val lastWeek = java.util.Calendar.getInstance().apply { add(java.util.Calendar.WEEK_OF_YEAR, -1) }
                calDue.get(java.util.Calendar.WEEK_OF_YEAR) == lastWeek.get(java.util.Calendar.WEEK_OF_YEAR) && calDue.get(java.util.Calendar.YEAR) == lastWeek.get(java.util.Calendar.YEAR)
            }
            org.dhis2.community.tasking.filters.models.DateRangeFilter.ThisMonth -> calDue.get(java.util.Calendar.MONTH) == today.get(java.util.Calendar.MONTH) && calDue.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
            org.dhis2.community.tasking.filters.models.DateRangeFilter.LastMonth -> {
                val lastMonth = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -1) }
                calDue.get(java.util.Calendar.MONTH) == lastMonth.get(java.util.Calendar.MONTH) && calDue.get(java.util.Calendar.YEAR) == lastMonth.get(java.util.Calendar.YEAR)
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
    fun setOrgUnitFilters(selectedOrgUnits: List<OrganisationUnit>) {
        // TODO("Not yet implemented")
    }
}
