package org.dhis2.community.tasking.filters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.dhis2.community.tasking.filters.models.DateRangeFilter
import org.dhis2.community.tasking.filters.models.FilterUiState
import org.dhis2.community.tasking.filters.models.TaskFilterModel
import org.hisp.dhis.mobile.ui.designsystem.component.CheckBoxData
import timber.log.Timber

class TaskFilterState {
    var uiState by mutableStateOf(FilterUiState())
    // Default status filter
    var currentFilter by mutableStateOf(
        TaskFilterModel(
            statusFilters = setOf(
                org.dhis2.community.tasking.ui.TaskingStatus.UPCOMING,
                org.dhis2.community.tasking.ui.TaskingStatus.DUE_TODAY,
                org.dhis2.community.tasking.ui.TaskingStatus.DUE_SOON,
                org.dhis2.community.tasking.ui.TaskingStatus.OVERDUE,
                org.dhis2.community.tasking.ui.TaskingStatus.DEFAULTED
            )
        )
    )
        private set

    fun updateProgramFilters(selectedPrograms: List<CheckBoxData>) {
        val selectedProgramIds = selectedPrograms.filter { it.checked }.map { it.uid }.toSet()
        Timber.d("updateProgramFilters called with: $selectedProgramIds")
        currentFilter = currentFilter.copy(programFilters = selectedProgramIds)
        updateUiState()
    }

    fun updateOrgUnitFilters(selectedOrgUnits: Set<String>) {
        Timber.d("updateOrgUnitFilters called with: $selectedOrgUnits")
        currentFilter = currentFilter.copy(orgUnitFilters = selectedOrgUnits)
        updateUiState()
    }

    fun updatePriorityFilters(selectedPriorities: List<CheckBoxData>) {
        val selectedPriorityEnums = selectedPriorities.filter { it.checked }
            .mapNotNull { org.dhis2.community.tasking.ui.TaskingPriority.entries.find { p -> p.label == it.uid } }
            .toSet()
        Timber.d("updatePriorityFilters called with: $selectedPriorityEnums")
        currentFilter = currentFilter.copy(priorityFilters = selectedPriorityEnums)
        updateUiState()
    }

    fun updateStatusFilters(selectedStatuses: List<CheckBoxData>) {
        val selectedStatusEnums = selectedStatuses.filter { it.checked }
            .mapNotNull { org.dhis2.community.tasking.ui.TaskingStatus.entries.find { s -> s.label == it.uid } }
            .toSet()
        Timber.d("updateStatusFilters called with: $selectedStatusEnums")
        currentFilter = currentFilter.copy(statusFilters = selectedStatusEnums)
        updateUiState()
    }

    fun updateDueDateFilter(dateRange: DateRangeFilter) {
        Timber.d("updateDueDateFilter called with: $dateRange")
        currentFilter = currentFilter.copy(dueDateRange = dateRange, customDateRange = null)
        updateUiState()
    }

    fun clearAllFilters() {
        Timber.d("clearAllFilters called")
        currentFilter = TaskFilterModel(dueDateRange = null, customDateRange = null)
        updateUiState()
    }

    private fun updateUiState() {
        Timber.d("updateUiState called. Current filter: $currentFilter")
        uiState = FilterUiState(
            isProgramFilterActive = currentFilter.programFilters.isNotEmpty(),
            programFilterCount = currentFilter.programFilters.size,
            isOrgUnitFilterActive = currentFilter.orgUnitFilters.isNotEmpty(),
            orgUnitFilterCount = currentFilter.orgUnitFilters.size,
            isPriorityFilterActive = currentFilter.priorityFilters.isNotEmpty(),
            priorityFilterCount = currentFilter.priorityFilters.size,
            isStatusFilterActive = currentFilter.statusFilters.isNotEmpty(),
            statusFilterCount = currentFilter.statusFilters.size,
            isDueDateFilterActive = currentFilter.dueDateRange != null,
            dueDateFilterCount = if (currentFilter.dueDateRange != null) 1 else 0,
            selectedDateRange = currentFilter.dueDateRange,
        )
    }
}
