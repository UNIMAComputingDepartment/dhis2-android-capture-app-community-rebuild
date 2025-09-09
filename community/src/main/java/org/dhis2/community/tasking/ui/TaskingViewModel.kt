package org.dhis2.community.tasking.ui

import android.os.Build
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.dhis2.community.tasking.engine.TaskingEvaluator
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
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val evaluator = TaskingEvaluator(d2, repository)
                    val evaluationResults = evaluator.evaluateForTie(
                        sourceTieUid = null,
                        programUid = "",
                        sourceTieOrgUnit = ""
                    )

                    allTasks = evaluationResults.mapNotNull { result ->
                        evaluationResultToTask(result)?.let { TaskingUiModel(it, result.orgUnit) }
                    }

                    updateFilterOptions()
                    applyFilters()
                } catch (e: Exception) {
                    Timber.e(e, "Error loading tasks")
                }
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
        applyFilters()
    }

    fun refreshTasks() {
        loadInitialData()
    }
}
