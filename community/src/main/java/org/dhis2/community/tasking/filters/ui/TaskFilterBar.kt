package org.dhis2.community.tasking.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import org.dhis2.community.tasking.filters.models.FilterUiState
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.theme.*
import androidx.compose.ui.tooling.preview.Preview
import timber.log.Timber

@Composable
fun TaskFilterBar(
    filterState: FilterUiState,
    onProgramFilterClick: () -> Unit,
    onOrgUnitFilterClick: () -> Unit,
    onPriorityFilterClick: () -> Unit,
    onStatusFilterClick: () -> Unit,
    onDueDateFilterClick: () -> Unit,
    onClearAllFilters: () -> Unit
) {
    Timber.d("TaskFilterBar composable rendered with state: $filterState")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Spacing8)
    ) {
        // Scrollable row for chips only
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Spacing8)
        ) {
            FilterChip(
                label = "Program",
                selected = filterState.isProgramFilterActive,
                onSelected = { Timber.d("Program filter chip clicked"); onProgramFilterClick() },
                badge = if (filterState.programFilterCount > 0) filterState.programFilterCount.toString() else null
            )
            FilterChip(
                label = "Org Unit",
                selected = filterState.isOrgUnitFilterActive,
                onSelected = { Timber.d("Org Unit filter chip clicked"); onOrgUnitFilterClick() },
                badge = if (filterState.orgUnitFilterCount > 0) filterState.orgUnitFilterCount.toString() else null
            )
            FilterChip(
                label = "Priority",
                selected = filterState.isPriorityFilterActive,
                onSelected = { Timber.d("Priority filter chip clicked"); onPriorityFilterClick() },
                badge = if (filterState.priorityFilterCount > 0) filterState.priorityFilterCount.toString() else null
            )
            FilterChip(
                label = "Status",
                selected = filterState.isStatusFilterActive,
                onSelected = { Timber.d("Status filter chip clicked"); onStatusFilterClick() },
                badge = if (filterState.statusFilterCount > 0) filterState.statusFilterCount.toString() else null
            )
            FilterChip(
                label = "Due Date",
                selected = filterState.selectedDateRange != null,
                onSelected = { Timber.d("Due Date filter chip clicked"); onDueDateFilterClick() },
                badge = if (filterState.dueDateFilterCount > 0) filterState.dueDateFilterCount.toString() else null
            )
            Spacer(modifier = Modifier.padding(end = 2.dp))
        }
        Button(
            modifier = Modifier
                .padding(start = 2.dp)
                .padding(horizontal = 2.dp),
            enabled = true,
            style = ButtonStyle.FILLED,
            colorStyle = ColorStyle.DEFAULT,
            text = "Clear",
            onClick = onClearAllFilters
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTaskFilterBar() {
    val filterState = FilterUiState(
        isProgramFilterActive = false,
        isOrgUnitFilterActive = false,
        isPriorityFilterActive = false,
        isStatusFilterActive = false,
        selectedDateRange = null,
        programFilterCount = 0,
        orgUnitFilterCount = 0,
        priorityFilterCount = 0,
        statusFilterCount = 0,
        dueDateFilterCount = 0
    )
    TaskFilterBar(
        filterState = filterState,
        onProgramFilterClick = {},
        onOrgUnitFilterClick = {},
        onPriorityFilterClick = {},
        onStatusFilterClick = {},
        onDueDateFilterClick = {},
        onClearAllFilters = {}
    )
}
