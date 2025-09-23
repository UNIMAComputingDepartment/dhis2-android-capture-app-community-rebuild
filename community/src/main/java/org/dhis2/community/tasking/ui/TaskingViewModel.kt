package org.dhis2.community.tasking.ui

import android.os.Build
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.dhis2.community.tasking.filters.TaskFilterState
import org.dhis2.community.tasking.filters.models.DateRangeFilter
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.mobile.ui.designsystem.component.CheckBoxData
import org.hisp.dhis.mobile.ui.designsystem.component.OrgTreeItem
import java.util.Calendar
import javax.inject.Inject

interface TaskingViewModelContract {
    val filteredTasks: StateFlow<List<TaskingUiModel>>
    val programs: List<CheckBoxData>
    val priorities: List<CheckBoxData>
    val statuses: List<CheckBoxData>
    val orgUnits: List<OrgTreeItem>
    val allTasksForProgress: List<TaskingUiModel>
    fun onFilterChanged()
    fun tasksForProgressBar(): List<TaskingUiModel>
    fun updateTasks(tasks: List<TaskingUiModel>) // Added method to handle updates from presenter
}

class TaskingViewModel @Inject constructor(
    private val repository: TaskingRepository,
    private val d2: D2
) : ViewModel(), TaskingViewModelContract {
    val filterState = TaskFilterState()
    private var allTasks: List<TaskingUiModel> = emptyList()
    private val _filteredTasks = MutableStateFlow<List<TaskingUiModel>>(emptyList())
    override val filteredTasks: StateFlow<List<TaskingUiModel>> = _filteredTasks

    override var programs: List<CheckBoxData> = emptyList()
        private set
    override var priorities: List<CheckBoxData> = emptyList()
        private set
    override var statuses: List<CheckBoxData> = emptyList()
        private set
    override var orgUnits: List<OrgTreeItem> = emptyList()
        private set
    override val allTasksForProgress: List<TaskingUiModel>
        get() = allTasks

    init {
        Log.d("TaskingViewModel", "TaskingViewModel initialized")
        loadInitialData()
    }

    private fun loadInitialData() {
        Log.d("TaskingViewModel", "loadInitialData() called in TaskingViewModel")
        viewModelScope.launch {
            Log.d("TaskingViewModel", "Coroutine launched in loadInitialData()")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Ensure config is loaded before accessing cachedConfig
                    repository.getTaskingConfig()
                    Log.d("TaskingViewModel", "About to fetch orgUnits from repository.currentOrgUnits")
                    val orgUnits = repository.currentOrgUnits
                    Log.d("TaskingViewModel", "Fetched orgUnits: $orgUnits")
                    val programUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.programUid
                    Log.d("TaskingViewModel", "Using programUid: $programUid")
                    val teiTypeUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.teiTypeUid
                    Log.d("TaskingViewModel", "Using teiTypeUid: $teiTypeUid")
                    val taskConfig = repository.getCachedConfig()?.programTasks?.firstOrNull { it.programUid == programUid }
                    Log.d("TaskingViewModel", "Using taskConfig: $taskConfig")
                    allTasks = orgUnits.flatMap { orgUnitUid ->
                        Log.d("TaskingViewModel", "Fetching tasks for orgUnit $orgUnitUid and programUid $programUid")
                        val teis = repository.getTaskTei(orgUnitUid)
                        Log.d("TaskingViewModel", "TEIs fetched for orgUnit $orgUnitUid: ${teis.size}")
//                        val tasks = repository.getAllTasks(orgUnitUid, programUid ?: "")
                        val tasks = repository.getAllTasks()
                        val tasksDebug = repository.getTasksPerOrgUnit(orgUnitUid)
                        Log.d("TaskingViewModel", "Tasks fetched for orgUnit $orgUnitUid: ${tasksDebug.size}")
                        Log.d("TaskingViewModel", "Tasks built for orgUnit $orgUnitUid: ${tasks.size}")
                        tasks.map { task -> TaskingUiModel(task, orgUnitUid, repository) }
                    }
                    Log.d("TaskingViewModel", "Total tasks loaded: ${allTasks.size}")
                    filterState.updateUiState()
                    updateFilterOptions()
                    applyFilters()
                } catch (e: Exception) {
                    Log.e("TaskingViewModel", "Error loading tasks in loadInitialData()", e)
                }
            } else {
                Log.w("TaskingViewModel", "Android version too low for loadInitialData() logic")
            }
        }
    }

    private fun evaluationResultToTask(result: EvaluationResult): Task? {
        return try {
            result.tieAttrs?.let { attrs ->
                result.dueDate?.let { dueDate ->
                    Task(
                        name = result.taskingConfig.name,
                        description = result.taskingConfig.description,
                        sourceProgramUid = result.programUid,
                        sourceEnrollmentUid = "",
                        sourceTeiUid = " ",
                        sourceProgramName =  "", //result.taskingConfig.trigger.programName,
                        teiUid = result.teiUid,
                        teiPrimary = attrs.first,
                        teiSecondary = attrs.second,
                        teiTertiary = attrs.third,
                        dueDate = dueDate,
                        priority = result.taskingConfig.priority,
                        status = "OPEN",
                        iconNane = repository.getSourceProgramIcon(result.programUid),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TaskingViewModel", "Error converting evaluation result to task", e)
            null
        }
    }

    private fun updateFilterOptions() {
        Log.d("TaskingViewModel", "updateFilterOptions called")
        // Update program filters
        programs = allTasks
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
        orgUnits = allTasks
            .mapNotNull { task ->
                task.orgUnit?.takeIf { it.isNotEmpty() }?.let { orgUnitUid ->
                    OrgTreeItem(uid = orgUnitUid, label = orgUnitUid)
                }
            }
            .distinctBy { it.uid }
    }

    private fun applyFilters() {
        Log.d("TaskingViewModel", "applyFilters called")
        val filter = filterState.currentFilter
        _filteredTasks.value = allTasks.filter { task ->
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
                    (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
                    (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
                    (filter.statusFilters.isEmpty() || filter.statusFilters.any { status -> status.label == task.status.label }) &&
                    matchesDateFilter(task, filter.dueDateRange)
        }
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
            else -> false
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
        Log.d("TaskingViewModel", "onFilterChanged called")
        applyFilters()
    }


    override fun tasksForProgressBar(): List<TaskingUiModel> {
        val filter = filterState.currentFilter
        return allTasks.filter { task ->
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            matchesDateFilter(task, filter.dueDateRange)
        }
    }

    fun refreshData() {
        Log.d("TaskingViewModel", "Refreshing tasks data")
        viewModelScope.launch {
            try {
                // Fetch fresh task data
                val updatedTasks = repository.getAllTasks()
                Log.d("TaskingViewModel", "Fetched ${updatedTasks.size} tasks")

                // Map tasks to UI models
                allTasks = updatedTasks.map { task ->
                    val orgUnit = repository.getOrgUnit(task.teiUid)
                    TaskingUiModel(task, orgUnit, repository).also { uiModel ->
                        Log.d("TaskingViewModel", "Task ${task.name} status: ${task.status} -> UI status: ${uiModel.status.label}")
                    }
                }

                // Update filters and UI
                updateFilterOptions()
                applyFilters()

                Log.d("TaskingViewModel", "Data refresh complete. Filtered tasks: ${_filteredTasks.value.size}")
            } catch (e: Exception) {
                Log.e("TaskingViewModel", "Error refreshing tasks", e)
            }
        }
    }

    override fun updateTasks(tasks: List<TaskingUiModel>) {
        Log.d("TaskingViewModel", "Updating tasks from presenter with ${tasks.size} tasks")
        allTasks = tasks
        updateFilterOptions()
        applyFilters()
    }
}
