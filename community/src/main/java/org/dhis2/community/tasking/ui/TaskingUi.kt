package org.dhis2.community.tasking.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import org.dhis2.community.tasking.filters.TaskFilterState
import org.dhis2.community.tasking.filters.ui.*
import org.dhis2.community.tasking.models.Task
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.component.state.*
import org.hisp.dhis.mobile.ui.designsystem.theme.*
import java.text.SimpleDateFormat
import java.util.*
import timber.log.Timber

const val TASKING_ITEMS = "TASKING_ITEMS"

@Composable
fun TaskingUi(
    tasks: List<TaskingUiModel>,
    onTaskClick: (TaskingUiModel) -> Unit,
    viewModel: TaskingViewModelContract,
    filterState: TaskFilterState
) {
    Timber.d("TaskingUi composable rendered with ${tasks.size} tasks")
    var activeFilterSheet by remember { mutableStateOf<FilterSheetType?>(null) }

    // Collect tasks from viewModel's StateFlow
    val filteredTasks by viewModel.filteredTasks.collectAsState()
    Timber.d("TaskingUi filteredTasks count: ${filteredTasks.size}")

    // Calculate progress variables at the top scope
    val allTasksForProgress = viewModel.allTasksForProgress.ifEmpty { tasks }
    val completedTaskCount = allTasksForProgress.count { it.status == TaskingStatus.COMPLETED }
    val totalTaskCount = allTasksForProgress.size
    val completionPercentage = if (totalTaskCount > 0) {
        (completedTaskCount * 100) / totalTaskCount
    } else 0
    Timber.d("TaskingUi progress: $completedTaskCount/$totalTaskCount completed ($completionPercentage%)")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TASKING_ITEMS)
            .padding(6.dp)
    ) {
        // Filter Bar
        TaskFilterBar(
            filterState = filterState.uiState,
            onProgramFilterClick = {
                Timber.d("Program filter clicked")
                activeFilterSheet = FilterSheetType.PROGRAM
            },
            onOrgUnitFilterClick = {
                Timber.d("OrgUnit filter clicked")
                activeFilterSheet = FilterSheetType.ORG_UNIT
            },
            onPriorityFilterClick = {
                Timber.d("Priority filter clicked")
                activeFilterSheet = FilterSheetType.PRIORITY
            },
            onStatusFilterClick = {
                Timber.d("Status filter clicked")
                activeFilterSheet = FilterSheetType.STATUS
            },
            onDueDateFilterClick = {
                Timber.d("DueDate filter clicked")
                activeFilterSheet = FilterSheetType.DUE_DATE
            },
            onClearAllFilters = {
                Timber.d("Clear all filters clicked")
                filterState.clearAllFilters()
                viewModel.onFilterChanged()
            }
        )

        // Task Progress Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 24.dp),
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
                    programs = viewModel.programs,
                    onDismiss = { activeFilterSheet = null },
                    onApplyFilters = { selected ->
                        filterState.updateProgramFilters(selected)
                        activeFilterSheet = null
                        viewModel.onFilterChanged()
                    }
                )
            }
            FilterSheetType.ORG_UNIT -> {
                OrgUnitFilterBottomSheet(
                    orgUnits = viewModel.orgUnits,
                    selectedOrgUnits = filterState.currentFilter.orgUnitFilters,
                    onDismiss = { activeFilterSheet = null },
                    onApplyFilters = {
                        activeFilterSheet = null
                        viewModel.onFilterChanged()
                    },
                    onItemSelected = { uid, checked ->
                        val updated = if (checked) filterState.currentFilter.orgUnitFilters + uid else filterState.currentFilter.orgUnitFilters - uid
                        filterState.updateOrgUnitFilters(updated)
                    }
                )
            }
            FilterSheetType.PRIORITY -> {
                PriorityFilterBottomSheet(
                    priorities = viewModel.priorities,
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
                    statuses = viewModel.statuses,
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
                        filterState.updateDueDateFilter(selectedRange ?: return@DueDateFilterBottomSheet)
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
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTasks) { task ->
                    val statusColor = task.status.color
                    val additionalInfoList = listOf(
                        AdditionalInfoItem(
                            key = "Name",
                            value = task.teiPrimary,
                            color = TextColor.OnSurface,
                            truncate = false,
                            isConstantItem = true
                        ),
                        AdditionalInfoItem(
                            key = "Details",
                            value = task.teiSecondary,
                            color = TextColor.OnSurface,
                            truncate = false,
                            isConstantItem = true
                        ),
                        AdditionalInfoItem(
                            key = "Date of Birth",
                            value = task.teiTertiary,
                            color = TextColor.OnSurface,
                            truncate = false,
                            isConstantItem = true
                        ),
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
                            isConstantItem = true
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
                            isConstantItem = true
                        )
                    )

                    val additionalInfoState = rememberAdditionalInfoColumnState(
                        additionalInfoList = additionalInfoList,
                        syncProgressItem = AdditionalInfoItem(
                            value = "",
                            color = TextColor.OnSurface,
                            isConstantItem = true
                        ),
                        minItemsToShow = 2,
                        scrollableContent = false
                    )

                    val listCardState = rememberListCardState(
                        title = ListCardTitleModel(
                            text = task.taskName,
                            color = SurfaceColor.Primary
                        ),
                        description = ListCardDescriptionModel(
                            text = task.sourceProgramName,
                            color = TextColor.OnSurface
                        ),
                        lastUpdated = task.dueDate?.let {
                            "Due: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)}"
                        },
                        additionalInfoColumnState = additionalInfoState,
                        loading = false,
                        shadow = true,
                        expandable = false,
                        itemVerticalPadding = 8.dp,
                        selectionState = SelectionState.NONE
                    )

                    ListCard(
                        modifier = Modifier.fillMaxWidth(),
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
                        }
                    )
                }
            }
        }
    }
}

private enum class FilterSheetType { PROGRAM, ORG_UNIT, PRIORITY, STATUS, DUE_DATE }

fun getDummyTasks() : List<TaskingUiModel> {
    return listOf(
        TaskingUiModel(
            task = Task(
                name = "BCG Immunization Due",
                description = "BCG Immunization for newborn",
                sourceProgramUid = "IpHINAT79UW",
                sourceEnrollmentUid = "enroll1",
                sourceProgramName = "Expanded Programme on Immunization - EPI",
                teiUid = "tei1",
                teiPrimary = "James Phiri",
                teiSecondary = "2025-08-15",
                teiTertiary = "Male",
                dueDate = "2025-09-06",
                priority = "Low",
                status = "OPEN",
                iconNane = ""
            ),
            orgUnit = ""
        ),
        TaskingUiModel(
            task = Task(
                name = "ANC Follow-up Visit",
                description = "Scheduled antenatal care follow-up",
                sourceProgramUid = "WSGAb5XwJ3Y",
                sourceEnrollmentUid = "enroll2",
                sourceProgramName = "CBMNC - Woman Program",
                teiUid = "tei2",
                teiPrimary = "Mary Banda",
                teiSecondary = "W123",
                teiTertiary = "MAT456",
                dueDate = "2025-09-10",
                priority = "Medium",
                status = "DUE_SOON",
                iconNane = ""
            ),
            orgUnit = ""
        ),
        TaskingUiModel(
            task = Task(
                name = "Post-delivery Follow-up",
                description = "Neonatal check-up required",
                sourceProgramUid = "uy2gU8kT1jF",
                sourceEnrollmentUid = "enroll3",
                sourceProgramName = "CBMNC - Neonatal Program",
                teiUid = "tei3",
                teiPrimary = "Baby Tembo",
                teiSecondary = "2025-09-01",
                teiTertiary = "MAT789",
                dueDate = "2025-09-01",
                priority = "High",
                status = "OVERDUE",
                iconNane = ""
            ),
            orgUnit = ""
        ),
        TaskingUiModel(
            task = Task(
                name = "OPV-1 Vaccination",
                description = "First dose of Oral Polio Vaccine",
                sourceProgramUid = "IpHINAT79UW",
                sourceEnrollmentUid = "enroll4",
                sourceProgramName = "EPI",
                teiUid = "tei4",
                teiPrimary = "Sarah Mwanza",
                teiSecondary = "2025-09-20",
                teiTertiary = "Female",
                dueDate = "2025-09-20",
                priority = "High",
                status = "COMPLETED",
                iconNane = ""
            ),
            orgUnit = ""
        )
    )

    /*val filterState = remember { TaskFilterState() }

    val filteredPreviewTasks = fakeTasks.filter {
        it.status != TaskingStatus.COMPLETED
    }

    val previewViewModel = object : TaskingViewModelContract {
        override val filteredTasks = MutableStateFlow(filteredPreviewTasks)
        override val programs = emptyList<CheckBoxData>()
        override val orgUnits = emptyList<OrgTreeItem>()
        override val priorities = emptyList<CheckBoxData>()
        override val statuses = emptyList<CheckBoxData>()
        override val allTasksForProgress: List<TaskingUiModel> = fakeTasks
        override fun onFilterChanged() {}
    }

    TaskingUi(
        tasks = fakeTasks,
        onTaskClick = {},
        viewModel = previewViewModel,
        filterState = filterState
    )*/
}