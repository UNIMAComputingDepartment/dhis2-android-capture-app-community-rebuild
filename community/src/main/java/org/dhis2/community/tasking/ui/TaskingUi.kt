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
    tasks: List<TaskingUiModel>,
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

    // For progress bar, use tasks filtered by all filters except status
    val progressTasks = viewModel.tasksForProgressBar()
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
                    action = {
                        Text(
                            text = data.visuals.actionLabel ?: "Dismiss",
                            color = SurfaceColor.Primary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    },
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
                FilterSheetType.ORG_UNIT -> {
//                OrgUnitFilterBottomSheet(
//                    orgUnits = viewModel.orgUnits,
//                    selectedOrgUnits = filterState.currentFilter.orgUnitFilters,
//                    onDismiss = { activeFilterSheet = null },
//                    onApplyFilters = {
//                        activeFilterSheet = null
//                        viewModel.onFilterChanged()
//                    },
//                    onItemSelected = { uid, checked ->
//                        val updated = if (checked) filterState.currentFilter.orgUnitFilters + uid else filterState.currentFilter.orgUnitFilters - uid
//                        filterState.updateOrgUnitFilters(updated)
//                    }
//                )
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
                    items(filteredTasks) { task ->
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
                                value = task.displayVillageName ?: "",
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
//                        val personName = task.teiPrimary
//                        val titleText = buildString {
//                            append(personName)
//                            if (personName.isNotBlank()) append("\n")
//                            append(task.taskName)
//                        }
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
                                val validEnrollment = task.sourceTeiUid
                                if (validEnrollment == null) {
                                    Log.d("TaskingUi", "Invalid enrollment for task: ${task.taskName}")
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "TEI enrollment is not valid",
                                            actionLabel = "Dismiss",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } else {
                                    Log.d("TaskingUi", "Valid enrollment for task: ${task.taskName}")
                                    onTaskClick(task)
                                }
                            },
                            onSizeChanged = {}
                        )
                    }
                }
            }
        }
    }
}

private enum class FilterSheetType { PROGRAM, ORG_UNIT, PRIORITY, STATUS, DUE_DATE }

//@Preview(showBackground = true)
//@Composable
//fun PreviewTaskingUi() {
//    val fakeTasks = getDummyTasks()
//    val filterState = remember { TaskFilterState() }
//    val filteredPreviewTasks = fakeTasks.filter { it.status != TaskingStatus.COMPLETED }
//    val previewViewModel = object : TaskingViewModelContract {
//        override val filteredTasks = MutableStateFlow(filteredPreviewTasks)
//        override val programs = emptyList<CheckBoxData>()
//        override val orgUnits = emptyList<OrgTreeItem>()
//        override val priorities = emptyList<CheckBoxData>()
//        override val statuses = emptyList<CheckBoxData>()
//        override val allTasksForProgress: List<TaskingUiModel> = fakeTasks
//        override fun onFilterChanged() {}
//        override fun tasksForProgressBar(): List<TaskingUiModel> = fakeTasks
//        override fun updateTasks(tasks: List<TaskingUiModel>) {}
//    }
//    TaskingUi(
//        tasks = fakeTasks,
//        onTaskClick = {},
//        viewModel = previewViewModel,
//        filterState = filterState,
//        onOrgUnitFilterSelected = {},
//        showFilterBar = true // Provide default value for preview
//    )
//}
//
//fun getDummyTasks(): List<TaskingUiModel> {
//    return listOf(
//        TaskingUiModel(
//            task = Task(
//                name = "BCG Immunization Due",
//                description = "BCG Immunization for newborn",
//                sourceProgramUid = "IpHINAT79UW",
//                sourceEnrollmentUid = "enroll1",
//                sourceProgramName = "Expanded Programme on Immunization - EPI",
//                sourceTeiUid = "tei1",
//                teiUid = "tei1",
//                teiPrimary = "James Phiri",
//                teiSecondary = "2025-08-15",
//                teiTertiary = "Male",
//                dueDate = "2025-09-06",
//                priority = "Low",
//                status = "OPEN",
//                iconNane = "",
//                progressCurrent = 0,
//                progressTotal = 4
//            ),
//            orgUnit = null,
//            repository = null
//        ),
//        TaskingUiModel(
//            task = Task(
//                name = "ANC Follow-up Visit",
//                description = "Scheduled antenatal care follow-up",
//                sourceProgramUid = "WSGAb5XwJ3Y",
//                sourceEnrollmentUid = "enroll2",
//                sourceProgramName = "CBMNC - Woman Program",
//                sourceTeiUid = "tei2",
//                teiUid = "tei2",
//                teiPrimary = "Mary Banda",
//                teiSecondary = "W123",
//                teiTertiary = "MAT456",
//                dueDate = "2025-09-10",
//                priority = "Medium",
//                status = "DUE_SOON",
//                iconNane = "",
//                progressCurrent = 1,
//                progressTotal = 4
//            ),
//            orgUnit = null,
//            repository = null
//        ),
//        TaskingUiModel(
//            task = Task(
//                name = "Post-delivery Follow-up",
//                description = "Neonatal check-up required",
//                sourceProgramUid = "uy2gU8kT1jF",
//                sourceEnrollmentUid = "enroll3",
//                sourceProgramName = "CBMNC - Neonatal Program",
//                sourceTeiUid = "tei3",
//                teiUid = "tei3",
//                teiPrimary = "Baby Tembo",
//                teiSecondary = "2025-09-01",
//                teiTertiary = "MAT789",
//                dueDate = "2025-09-01",
//                priority = "High",
//                status = "OVERDUE",
//                iconNane = "",
//                progressCurrent = 2,
//                progressTotal = 4
//            ),
//            orgUnit = null,
//            repository = null
//        ),
//        TaskingUiModel(
//            task = Task(
//                name = "OPV-1 Vaccination",
//                description = "First dose of Oral Polio Vaccine",
//                sourceProgramUid = "IpHINAT79UW",
//                sourceEnrollmentUid = "enroll4",
//                sourceProgramName = "EPI",
//                sourceTeiUid = "tei4",
//                teiUid = "tei4",
//                teiPrimary = "Sarah Mwanza",
//                teiSecondary = "2025-09-20",
//                teiTertiary = "Female",
//                dueDate = "2025-09-20",
//                priority = "High",
//                status = "COMPLETED",
//                iconNane = "",
//                progressCurrent = 4,
//                progressTotal = 4
//            ),
//            orgUnit = null,
//            repository = null
//        )
//    )
//}