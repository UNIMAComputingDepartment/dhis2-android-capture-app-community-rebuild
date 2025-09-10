package org.dhis2.community.tasking.ui

import android.os.Build
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
import timber.log.Timber
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
        Timber.d("TaskingViewModel initialized")
        loadInitialData()
    }

    private fun loadInitialData() {
        Timber.d("loadInitialData() called in TaskingViewModel")
        viewModelScope.launch {
            Timber.d("Coroutine launched in loadInitialData()")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Ensure config is loaded before accessing cachedConfig
                    repository.getTaskingConfig()
                    Timber.d("About to fetch orgUnits from repository.currentOrgUnits")
                    val orgUnits = repository.currentOrgUnits
                    Timber.d("Fetched orgUnits: $orgUnits")
                    val programUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.programUid
                    Timber.d("Using programUid: $programUid")
                    val teiTypeUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.teiTypeUid
                    Timber.d("Using teiTypeUid: $teiTypeUid")
                    val taskConfig = repository.getCachedConfig()?.programTasks?.firstOrNull { it.programUid == programUid }
                    Timber.d("Using taskConfig: $taskConfig")
                    allTasks = orgUnits.flatMap { orgUnitUid ->
                        Timber.d("Fetching tasks for orgUnit $orgUnitUid and programUid $programUid")
                        val teis = repository.getTaskTei(orgUnitUid)
                        Timber.d("TEIs fetched for orgUnit $orgUnitUid: ${teis.size}")
                        val tasks = repository.getAllTasks(orgUnitUid, programUid ?: "")
                        Timber.d("Tasks built for orgUnit $orgUnitUid: ${tasks.size}")
                        tasks.map { task -> TaskingUiModel(task, orgUnitUid) }
                    }
                    Timber.d("Total tasks loaded: ${allTasks.size}")
                    _filteredTasks.value = allTasks
                    updateFilterOptions()
                    applyFilters()
                } catch (e: Exception) {
                    Timber.e(e, "Error loading tasks in loadInitialData()")
                }
            } else {
                Timber.w("Android version too low for loadInitialData() logic")
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
                        sourceProgramName = result.taskingConfig.trigger.programName,
                        teiUid = result.teiUid,
                        teiPrimary = attrs.first,
                        teiSecondary = attrs.second,
                        teiTertiary = attrs.third,
                        dueDate = dueDate,
                        priority = result.taskingConfig.priority,
                        status = "OPEN"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error converting evaluation result to task")
            null
        }
    }

    private fun updateFilterOptions() {
        Timber.d("updateFilterOptions called")
        // Update program filters
        programs = allTasks
            .map { it.sourceProgramUid to it.sourceProgramName }
            .distinctBy { it.first }
            .map { (uid, name) ->
                CheckBoxData(
                    uid = uid,
                    checked = filterState.currentFilter.programFilters.contains(uid),
                    enabled = true,
                    textInput = AnnotatedString(name)
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
        Timber.d("applyFilters called")
        val filter = filterState.currentFilter
        _filteredTasks.value = allTasks.filter { task ->
            (filter.programFilters.isEmpty() || filter.programFilters.contains(task.sourceProgramUid)) &&
            (filter.orgUnitFilters.isEmpty() || filter.orgUnitFilters.contains(task.orgUnit)) &&
            (filter.priorityFilters.isEmpty() || filter.priorityFilters.contains(task.priority)) &&
            (filter.statusFilters.isEmpty() || filter.statusFilters.contains(task.status)) &&
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
            DateRangeFilter.ThisWeek -> {
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
            DateRangeFilter.ThisMonth -> {
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
            else -> false
        }
    }

    override fun onFilterChanged() {
        Timber.d("onFilterChanged called")
        applyFilters()
    }

    fun refreshTasks() {
        loadInitialData()
    }
}
