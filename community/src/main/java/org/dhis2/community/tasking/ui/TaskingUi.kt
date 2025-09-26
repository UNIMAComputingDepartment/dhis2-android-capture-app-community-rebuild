package org.dhis2.community.tasking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.dhis2.community.tasking.filters.TaskFilterState
import org.dhis2.community.tasking.filters.ui.DueDateFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.PriorityFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.ProgramFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.StatusFilterBottomSheet
import org.dhis2.community.tasking.filters.ui.TaskFilterBar
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

const val TASKING_ITEMS = "TASKING_ITEMS"

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

    // Collect tasks from viewModel's StateFlow
    val filteredTasks by viewModel.filteredTasks.collectAsState()

    // Collect progress tasks from ViewModel's StateFlow
    val progressTasks by viewModel.progressTasks.collectAsState()

    // Calculate progress bar data
    val completedTaskCount = progressTasks.count { it.status == TaskingStatus.COMPLETED }
    val totalTaskCount = progressTasks.size
    if (totalTaskCount > 0) {
        (completedTaskCount * 100) / totalTaskCount
    } else 0
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

            TaskProgressSection(
                completedCount = completedTaskCount,
                totalCount = totalTaskCount
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

            // Display filtered tasks
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks available",
                        color = TextColor.OnSurface
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredTasks) {
                        TaskItem(it, onTaskClick ={
                            onTaskClick(it)
                        })
                    }
                }
            }
        }
    }
}

private enum class FilterSheetType { PROGRAM, PRIORITY, STATUS, DUE_DATE }