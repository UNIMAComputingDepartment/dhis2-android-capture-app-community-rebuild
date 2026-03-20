package org.dhis2.community.tasking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.dhis2.community.IchisPrimary
import org.dhis2.community.tasking.filters.TaskFilterState
import org.dhis2.community.tasking.filters.ui.DueDateFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.PriorityFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.ProgramFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.StatusFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.TaskFilterBar
import org.dhis2.community.tasking.notifications.TaskReminderScheduler
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import timber.log.Timber

const val TASKING_ITEMS = "TASKING_ITEMS"

enum class TaskListViewType {
    LIST, GROUPED
}

data class PatientTaskGroup(
    val patientName: String,
    val patientUid: String,
    val taskCount: Int,
    val tasks: List<TaskingUiModel>
)

@Composable
fun TaskingUi(
    onTaskClick: (TaskingUiModel) -> Unit,
    viewModel: TaskingViewModelContract,
    filterState: TaskFilterState,
    onOrgUnitFilterSelected: () -> Unit,
    showFilterBar: Boolean
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var activeFilterSheet by remember { mutableStateOf<FilterSheetType?>(null) }
    var viewType by remember { mutableStateOf(TaskListViewType.GROUPED) }  // Default to GROUPED view

    // Collect tasks from viewModel's StateFlow
    val filteredTasks by viewModel.filteredTasks.collectAsState()

    // Collect progress tasks from ViewModel's StateFlow
    val progressTasks by viewModel.progressTasks.collectAsState()

    // Calculate progress bar data
    val completedTaskCount = progressTasks.count { it.status == TaskingStatus.COMPLETED }
    val totalTaskCount = progressTasks.size

    // Group tasks by patient (TEI) for grouped view
    val groupedTasks = remember(filteredTasks) {
        filteredTasks.groupBy { it.teiPrimary }
            .map { (patientName, tasks) ->
                PatientTaskGroup(
                    patientName = patientName.takeIf { it.isNotBlank() } ?: "Unknown Patient",
                    patientUid = tasks.firstOrNull()?.teiUid ?: "",
                    taskCount = tasks.size,
                    tasks = tasks
                )
            }
            .sortedBy { it.patientName }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    containerColor = TextColor.OnSurface,
                    content = {
                        Text(
                            text = data.visuals.message,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag(TASKING_ITEMS)
            .padding(6.dp)
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(Color.White)
        ) {
            // Filter Bar with Animation
            AnimatedVisibility(visible = showFilterBar) {
                TaskFilterBar(
                    filterState = filterState.uiState,
                    onProgramFilterClick = {
                        activeFilterSheet = FilterSheetType.PROGRAM
                    },
                    onOrgUnitFilterClick = {
                        onOrgUnitFilterSelected()
                    },
                    onPriorityFilterClick = {
                        activeFilterSheet = FilterSheetType.PRIORITY
                    },
                    onStatusFilterClick = {
                        activeFilterSheet = FilterSheetType.STATUS
                    },
                    onDueDateFilterClick = {
                        activeFilterSheet = FilterSheetType.DUE_DATE
                    },
                    onClearAllFilters = {
                        filterState.clearAllFilters()
                        viewModel.onFilterChanged()
                    }
                )
            }

            // Progress Section with View Toggle (Sticky)
            TaskProgressSectionWithToggle(
                completedCount = completedTaskCount,
                totalCount = totalTaskCount,
                currentViewType = viewType,
                onViewTypeChanged = { viewType = it }
            )

            // Show bottom sheets for filters
            when (activeFilterSheet) {
                FilterSheetType.PROGRAM -> {
                    ProgramFilterBottomSheet(
                        programs = viewModel.programs.map { it.copy(checked = filterState.currentFilter.programFilters.contains(it.uid)) },
                        onDismiss = { activeFilterSheet = null },
                        onApplyFilters = { selected ->
                            filterState.updateProgramFilters(selected)
                            activeFilterSheet = null
                            viewModel.onFilterChanged()
                        }
                    )
                }
                FilterSheetType.PRIORITY -> {
                    PriorityFilterBottomSheet(
                        priorities = viewModel.priorities.map { it.copy(checked = filterState.currentFilter.priorityFilters.any { p -> p.label == it.uid }) },
                        onDismiss = { activeFilterSheet = null },
                        onApplyFilters = { selected ->
                            filterState.updatePriorityFilters(selected)
                            activeFilterSheet = null
                            viewModel.onFilterChanged()
                        }
                    )
                }
                FilterSheetType.STATUS -> {
                    StatusFilterBottomSheet(
                        statuses = viewModel.statuses.map { it.copy(checked = filterState.currentFilter.statusFilters.any { s -> s.label == it.uid }) },
                        onDismiss = { activeFilterSheet = null },
                        onApplyFilters = { selected ->
                            filterState.updateStatusFilters(selected)
                            activeFilterSheet = null
                            viewModel.onFilterChanged()
                        }
                    )
                }
                FilterSheetType.DUE_DATE -> {
                    DueDateFilterBottomSheet(
                        selectedRange = filterState.uiState.selectedDateRange,
                        onDismiss = { activeFilterSheet = null },
                        onApplyFilters = { selectedRange ->
                            if (selectedRange != null) {
                                filterState.updateDueDateFilter(selectedRange)
                            } else {
                                filterState.clearDueDateFilter()
                            }
                            activeFilterSheet = null
                            viewModel.onFilterChanged()
                        }
                    )
                }
                null -> {}
            }

            // Display filtered tasks or empty state
            when {
                filteredTasks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks available",
                            textAlign = TextAlign.Center,
                            color = TextColor.OnSurface
                        )
                    }
                }
                viewType == TaskListViewType.LIST -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredTasks) {
                            TaskItem(it, onTaskClick = {
                                onTaskClick(it)
                            })
                        }
                    }
                }
                else -> {
                    // Grouped view
                    TaskListGroupedView(
                        groups = groupedTasks,
                        onTaskClick = onTaskClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskProgressSectionWithToggle(
    completedCount: Int,
    totalCount: Int,
    currentViewType: TaskListViewType,
    onViewTypeChanged: (TaskListViewType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // Progress section
        TaskProgressSection(
            completedCount = completedCount,
            totalCount = totalCount,
            modifier = Modifier
        )

        // View toggle segmented buttons below progress bar
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // Reordered: GROUPED on left (index 0), LIST on right (index 1)
            val viewOptions = listOf(TaskListViewType.GROUPED, TaskListViewType.LIST)
            val selectedIndex = if (currentViewType == TaskListViewType.GROUPED) 0 else 1

            viewOptions.forEachIndexed { index, viewType ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = viewOptions.size
                    ),
                    onClick = { onViewTypeChanged(viewType) },
                    selected = index == selectedIndex,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = IchisPrimary,
                        activeContentColor = Color.White,
                        activeBorderColor = IchisPrimary,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = TextColor.OnSurfaceLight,
                        inactiveBorderColor = TextColor.OnSurfaceLight.copy(alpha = 0.3f)
                    ),
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (viewType == TaskListViewType.GROUPED) {
                                    Icons.Default.ViewAgenda
                                } else {
                                    Icons.AutoMirrored.Filled.ViewList
                                },
                                contentDescription = if (viewType == TaskListViewType.GROUPED) "Grouped View" else "List View",
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (viewType == TaskListViewType.GROUPED) "Grouped" else "List"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskListGroupedView(
    groups: List<PatientTaskGroup>,
    onTaskClick: (TaskingUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track expanded state for only ONE group at a time (accordion behavior)
    // Store the UID of the currently expanded group (null if none expanded)
    val expandedGroupUid = remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(groups.size, key = { groupIndex -> groups[groupIndex].patientUid }) { groupIndex ->
            val group = groups[groupIndex]
            // Check if THIS group is the currently expanded one
            val isExpanded = expandedGroupUid.value == group.patientUid

            Timber.d("TaskListGroupedView: Rendering group ${group.patientName}, expanded=$isExpanded")

            PatientGroupSection(
                group = group,
                onTaskClick = onTaskClick,
                isExpanded = isExpanded,
                onExpandedChange = { expanded ->
                    Timber.d("TaskListGroupedView: onExpandedChange for ${group.patientName}: $expanded")
                    // Accordion behavior: only one group can be expanded at a time
                    if (expanded) {
                        // Opening this group - close any previously open group
                        expandedGroupUid.value = group.patientUid
                    } else {
                        // Closing this group
                        expandedGroupUid.value = null
                    }
                }
            )
        }
    }
}

private enum class FilterSheetType { PROGRAM, PRIORITY, STATUS, DUE_DATE }