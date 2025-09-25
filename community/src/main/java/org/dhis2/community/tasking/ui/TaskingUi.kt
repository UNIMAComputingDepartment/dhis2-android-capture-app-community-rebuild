package org.dhis2.community.tasking.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.dhis2.community.tasking.filters.TaskFilterState
import org.dhis2.community.tasking.filters.ui.*
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.component.state.*
import org.hisp.dhis.mobile.ui.designsystem.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.community.tasking.models.Task
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

const val TASKING_ITEMS = "TASKING_ITEMS"

@Composable
fun TaskingUi(
    onTaskClick: (TaskingUiModel) -> Unit,
    viewModel: TaskingViewModelContract,
    filterState: TaskFilterState,
    onOrgUnitFilterSelected: () -> Unit,
    showFilterBar: Boolean
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeFilterSheet by remember { mutableStateOf<FilterSheetType?>(null) }

    // Collect tasks from viewModel's StateFlow
    val filteredTasks by viewModel.filteredTasks.collectAsState()

    // Collect progress tasks from ViewModel's StateFlow
    val progressTasks by viewModel.progressTasks.collectAsState()

    // Calculate progress bar data
    val completedTaskCount = progressTasks.count { it.status == TaskingStatus.COMPLETED }
    val totalTaskCount = progressTasks.size
    val completionPercentage = if (totalTaskCount > 0) {
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

            // Task Progress Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Task Progress",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextColor.OnSurface
                        )
                        Text(
                            text = "$completedTaskCount of $totalTaskCount completed ($completionPercentage%)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextColor.OnSurface
                        )
                    }
                    ProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .padding(top = 8.dp),
                        type = ProgressIndicatorType.LINEAR,
                        progress = if (totalTaskCount > 0) {
                            completedTaskCount.toFloat() / totalTaskCount
                        } else 0f
                    )
                }
            }

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
                        TaskItem(it) {
                            onTaskClick(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItem(task: TaskingUiModel, onTaskClick: (TaskingUiModel) -> Unit)  {
    val statusColor = task.status.color
    val infoList = listOf(
        AdditionalInfoItem(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = task.priority.color,
                    modifier = Modifier.size(24.dp)
                )
            },
            value = task.priority.label,
            color = task.priority.color,
            truncate = false,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            value = task.status.label,
            color = statusColor,
            truncate = false,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = task.progressColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            value = task.progressDisplay,
            color = task.progressColor,
            truncate = false,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            key = "Name",
            value = task.teiPrimary,
            color = TextColor.OnSurface,
            truncate = false,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            key = "Village Name",
            value = task.teiSecondary,
            color = TextColor.OnSurface,
            truncate = false,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            key = "Date of Birth",
            value = task.teiTertiary,
            color = TextColor.OnSurface,
            truncate = false,
            isConstantItem = false
        )
    )
    val additionalInfoState = rememberAdditionalInfoColumnState(
        additionalInfoList = infoList,
        syncProgressItem = AdditionalInfoItem(
            value = "",
            color = TextColor.OnSurface,
            isConstantItem = true
        ),
        expandLabelText = "Show more",
        shrinkLabelText = "Show less",
        minItemsToShow = 3,
        scrollableContent = false
    )
    val listCardState = rememberListCardState(
        title = ListCardTitleModel(
            text = task.taskName,
            color = SurfaceColor.Primary
        ),
        description = ListCardDescriptionModel(
            text = task.displayProgramName,
            color = TextColor.OnSurface
        ),
        lastUpdated = task.dueDate?.let {
            "Due: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)}"
        },
        additionalInfoColumnState = additionalInfoState,
        loading = false,
        shadow = true,
        itemVerticalPadding = 8.dp,
        selectionState = SelectionState.NONE
    )
    ListCard(
        modifier = Modifier.padding(8.dp),
        listCardState = listCardState,
        listAvatar = {
            Avatar(
                style = AvatarStyleData.Metadata(
                    imageCardData = task.metadataIconData.imageCardData,
                    avatarSize = MetadataAvatarSize.S(),
                    tintColor = task.metadataIconData.color
                )
            )
        },
        onCardClick = {
            onTaskClick(task)
        },
        onSizeChanged = {}
    )
}

private enum class FilterSheetType { PROGRAM, PRIORITY, STATUS, DUE_DATE }