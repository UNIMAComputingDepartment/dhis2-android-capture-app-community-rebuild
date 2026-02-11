package org.dhis2.community.tasking.ui

import android.os.Build
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.community.tasking.engine.DefaultingEvaluator
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.community.tasking.filters.TaskFilterState
import org.dhis2.community.tasking.filters.models.DateRangeFilter
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.mobile.ui.designsystem.component.CheckBoxData
import org.hisp.dhis.mobile.ui.designsystem.component.OrgTreeItem
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

interface TaskingViewModelContract {
    val loading: StateFlow<Boolean>
    val filteredTasks: StateFlow<List<TaskingUiModel>>
    val programs: List<CheckBoxData>
    val priorities: List<CheckBoxData>
    val statuses: List<CheckBoxData>
    val orgUnits: List<OrgTreeItem>
    val allTasksForProgress: List<TaskingUiModel>
    val progressTasks: StateFlow<List<TaskingUiModel>>
    fun onFilterChanged()
    fun tasksForProgressBar(): List<TaskingUiModel>
    fun updateTasks(tasks: List<TaskingUiModel>)
}

class TaskingViewModel @Inject constructor(
    private val repository: TaskingRepository,
    private val filterRepository: TaskFilterRepository,
) : ViewModel(), TaskingViewModelContract {
    val filterState = TaskFilterState()

    private val _loading = MutableStateFlow(false)
    override val loading = _loading.asStateFlow()

    private val _allTasks = MutableStateFlow<List<TaskingUiModel>>(emptyList())

    private val _filteredTasks = MutableStateFlow<List<TaskingUiModel>>(emptyList())
    override val filteredTasks: StateFlow<List<TaskingUiModel>> = _filteredTasks

    private val _progressTasks = MutableStateFlow<List<TaskingUiModel>>(emptyList())
    override val progressTasks: StateFlow<List<TaskingUiModel>> = _progressTasks

    override var programs: List<CheckBoxData> = emptyList()
        private set
    override var priorities: List<CheckBoxData> = emptyList()
        private set
    override var statuses: List<CheckBoxData> = emptyList()
        private set
    override var orgUnits: List<OrgTreeItem> = emptyList()
        private set

    // Return current tasks from StateFlow
    override val allTasksForProgress: List<TaskingUiModel>
        get() = _allTasks.value

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Ensure config is loaded before accessing cachedConfig
                    repository.getTaskingConfig()
                    val orgUnits = repository.currentOrgUnits

                    val tasks = orgUnits.flatMap { orgUnitUid ->
                        val fetchedTasks = repository.getAllTasks()

                        fetchedTasks.map { task -> TaskingUiModel(task, orgUnitUid, repository) }
                    }

                    _allTasks.value = tasks

                    filterState.updateUiState()
                    updateFilterOptions()
                    applyFilters()
                } catch (e: Exception) {
                    Timber.tag("TaskingViewModel").e(e, "Error loading tasks in loadInitialData()")
                }
            } else {
                Timber.tag("TaskingViewModel")
                    .w("Android version too low for loadInitialData() logic")
            }
        }
    }

    private fun updateFilterOptions() {
        Log.d("TaskingViewModel", "updateFilterOptions called")
        // Update program filters
        programs = _allTasks.value
            .map { it.sourceProgramUid to it.sourceProgramName }
            .distinctBy { it.first }
            .map { (uid, name) ->
                val displayName = repository.getProgramDisplayName(uid) ?: name
                CheckBoxData(
                    uid = uid,
                    checked = filterState.currentFilter.programFilters.contains(uid),
                    enabled = true,
                    textInput = AnnotatedString(displayName)
                )
            }

        // Update priority filters
        priorities = TaskingPriority.entries.map { priority ->
            CheckBoxData(
                uid = priority.label,
                checked = filterState.currentFilter.priorityFilters.contains(priority),
                enabled = true,
                textInput = AnnotatedString(priority.label)
            )
        }

        // Update status filters
        statuses = TaskingStatus.entries.map { status ->
            CheckBoxData(
                uid = status.label,
                checked = filterState.currentFilter.statusFilters.contains(status),
                enabled = true,
                textInput = AnnotatedString(status.label)
            )
        }

        // Update organization unit filters
        orgUnits = _allTasks.value
            .mapNotNull { task ->
                task.orgUnit?.takeIf { it.isNotEmpty() }?.let { orgUnitUid ->
                    OrgTreeItem(uid = orgUnitUid, label = orgUnitUid)
                }
            }
            .distinctBy { it.uid }
    }

    private fun applyFilters() {
        Timber.tag("TaskingViewModel").d("applyFilters called")
        val filter = filterState.currentFilter
        val allTasks = _allTasks.value
        // Task list: filter by all filters including status
        _filteredTasks.value = allTasks.filter { task ->
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            (filter.statusFilters.isEmpty() || filter.statusFilters.any { status -> status.label == task.status.label }) &&
            matchesDateFilter(task, filter.dueDateRange)
        }
        updateProgressTasks()
    }

    private fun updateProgressTasks() {
        val filter = filterState.currentFilter
        val allTasks = _allTasks.value
        // Progress bar: filter by all filters except status
        val progressFiltered = allTasks.filter { task ->
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
                    (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
                    (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
                    matchesDateFilter(task, filter.dueDateRange)
        }
        _progressTasks.value = progressFiltered
    }

    private fun matchesDateFilter(task: TaskingUiModel, dateRange: DateRangeFilter?): Boolean {
        if (dateRange == null) return true
        val taskDate = task.dueDate ?: return false
        val today = Calendar.getInstance()
        val taskCal = Calendar.getInstance().apply { time = taskDate }
        return when (dateRange) {
            DateRangeFilter.Today -> {
                if (exemptOverDueTask(task) ) {
                    true
                } else
                taskCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            }
            DateRangeFilter.Yesterday -> {
                val yesterday = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                taskCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
            }
            DateRangeFilter.Tomorrow -> {
                if (exemptOverDueTask(task)) {
                    true
                } else {
                val tomorrow = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, +1)
                }
                taskCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
            }
            }
            DateRangeFilter.ThisWeek -> {
                if (exemptOverDueTask(task) ) {
                    true
                } else
                taskCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.WEEK_OF_YEAR) == today.get(Calendar.WEEK_OF_YEAR)
            }
            DateRangeFilter.LastWeek -> {
                val lastWeek = Calendar.getInstance().apply {
                    add(Calendar.WEEK_OF_YEAR, -1)
                }
                taskCal.get(Calendar.YEAR) == lastWeek.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.WEEK_OF_YEAR) == lastWeek.get(Calendar.WEEK_OF_YEAR)
            }
            DateRangeFilter.NextWeek -> {
                if (exemptOverDueTask(task)) {
                    true
                } else {
                    val nextWeek = Calendar.getInstance().apply {
                    add(Calendar.WEEK_OF_YEAR, +1)
                }
                    taskCal.get(Calendar.YEAR) == nextWeek.get(Calendar.YEAR) &&
                            taskCal.get(Calendar.WEEK_OF_YEAR) == nextWeek.get(Calendar.WEEK_OF_YEAR)
                }
            }
            DateRangeFilter.ThisMonth -> {
                if (exemptOverDueTask(task) ) {
                    true
                } else
                taskCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.MONTH) == today.get(Calendar.MONTH)
            }
            DateRangeFilter.LastMonth -> {
                val lastMonth = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                }
                taskCal.get(Calendar.YEAR) == lastMonth.get(Calendar.YEAR) &&
                        taskCal.get(Calendar.MONTH) == lastMonth.get(Calendar.MONTH)
            }
            DateRangeFilter.NextMonth -> {
                if (exemptOverDueTask(task)) {
                    true
                } else {
                    val nextMonth = Calendar.getInstance().apply {
                    add(Calendar.MONTH, +1)
                }
                    taskCal.get(Calendar.YEAR) == nextMonth.get(Calendar.YEAR) &&
                            taskCal.get(Calendar.MONTH) == nextMonth.get(Calendar.MONTH)
                }
            }
        }
    }

    fun exemptOverDueTask(task: TaskingUiModel): Boolean {
        return if (task.status == TaskingStatus.OVERDUE) {
            val today = Calendar.getInstance()
            val taskCal = Calendar.getInstance().apply { task.dueDate?.let { time = it } }
            taskCal.before(today)
        } else false
    }

    override fun onFilterChanged() {
        applyFilters()
    }

    override fun tasksForProgressBar(): List<TaskingUiModel> {
        val filter = filterState.currentFilter
        val allTasks = _allTasks.value
        return allTasks.filter { task ->
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            matchesDateFilter(task, filter.dueDateRange)
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                // Fetch fresh task data
                val updatedTasks = repository.getAllTasks()
                Log.d("TaskingViewModel", "Fetched ${updatedTasks.size} tasks")

                // Map tasks to UI models
                val tasks = updatedTasks.map { task ->
                    val orgUnit = repository.getOrgUnit(task.teiUid)
                    TaskingUiModel(task, orgUnit, repository).also { uiModel ->
                        Log.d("TaskingViewModel", "Task ${task.name} status: ${task.status} -> UI status: ${uiModel.status.label}")
                    }
                }

                _allTasks.value = tasks

                // Update filters and UI
                updateFilterOptions()
                applyFilters()
            } catch (e: Exception) {
                Log.e("TaskingViewModel", "Error refreshing tasks", e)
            }
        }
    }

    override fun updateTasks(tasks: List<TaskingUiModel>) {
        _allTasks.value = tasks
        updateFilterOptions()
        applyFilters()
        refreshProgressTasks()
    }

    fun refreshProgressTasks() {
        _progressTasks.value = tasksForProgressBar()
    }

    fun reloadTasks() {
        viewModelScope.launch {
            try {
                //  Clear current list and show loading
                _loading.value = true
                _allTasks.value = emptyList()
                _filteredTasks.value = emptyList()
                Log.d("TaskingViewModel", "LOADING STARTED: _loading.value = true")

                //  Reset filters to default on every reload
                filterState.resetToDefaultFilters()

                //  Fetch fresh data from database
                val freshTasks = withContext(Dispatchers.IO) {
                    val orgUnits = repository.currentOrgUnits
                    orgUnits.flatMap { orgUnitUid ->
                        val tasks = repository.getAllTasks()
                        tasks.map { task ->
                            TaskingUiModel(
                                task = task,
                                orgUnit = orgUnitUid,
                                repository = repository
                            )
                        }
                    }
                }

                Log.d("TaskingViewModel", "Fetched ${freshTasks.size} fresh tasks from database")

                //  Add minimum delay to ensure loading animation is visible (at least 500ms)
                kotlinx.coroutines.delay(500)

                //  Update tasks with fresh data
                _allTasks.value = freshTasks
                Log.d("TaskingViewModel", "Updated _allTasks with ${freshTasks.size} tasks")

                //  Reapply filters
                updateFilterOptions()
                applyFilters()

                Log.d("TaskingViewModel", "Tasks reloaded: ${freshTasks.size} tasks found")

            } catch (e: Exception) {
                Log.e("TaskingViewModel", "Error reloading tasks", e)
                _allTasks.value = emptyList()
                _filteredTasks.value = emptyList()
            } finally {
                //  FORCE: Hide loading animation
                _loading.value = false
                Log.d("TaskingViewModel", "LOADING FINISHED: _loading.value = false")
            }
        }
    }

    fun setOrgUnitFilters(selectedOrgUnits: List<OrganisationUnit>) {
        val orgUnitUids = selectedOrgUnits.map { it.uid() }.toSet()
        val currentFilter = filterRepository.selectedFilters.value
        filterRepository.updateFilter(currentFilter.copy(orgUnitFilters = orgUnitUids))
    }

    fun canOpenTask(task: TaskingUiModel): Boolean {
        return try {
           val enrollment = repository.getEnrollment(task.sourceEnrollmentUid)
            enrollment != null
        } catch (e: Exception) {
            DefaultingEvaluator(repository).evaluateForDefaultingEnrollment(listOf(task.sourceEnrollmentUid))
            Timber.tag("TaskingViewModel").e(e, "Error checking access for program ${task.sourceProgramUid}")
            false
        }
    }
}