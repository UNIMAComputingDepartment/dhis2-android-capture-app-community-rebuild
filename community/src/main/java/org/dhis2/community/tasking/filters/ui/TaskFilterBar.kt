package org.dhis2.community.tasking.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp), // overall padding
        horizontalArrangement = Arrangement.spacedBy(Spacing.Spacing8)
    ) {
        // Static filter icon on the left
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = "Filter Icon",
            modifier = Modifier.padding(top = 12.dp)
        )

        // Scrollable row for chips & clear button
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .weight(1f), // take remaining space
            horizontalArrangement = Arrangement.spacedBy(Spacing.Spacing8)
        ) {
            FilterChip(
                label = "Program",
                selected = filterState.isProgramFilterActive,
                onSelected = { onProgramFilterClick() },
                badge = if (filterState.programFilterCount > 0) filterState.programFilterCount.toString() else null
            )
            FilterChip(
                label = "Org Unit",
                selected = filterState.isOrgUnitFilterActive,
                onSelected = { onOrgUnitFilterClick() },
                badge = if (filterState.orgUnitFilterCount > 0) filterState.orgUnitFilterCount.toString() else null
            )
            FilterChip(
                label = "Priority",
                selected = filterState.isPriorityFilterActive,
                onSelected = { onPriorityFilterClick() },
                badge = if (filterState.priorityFilterCount > 0) filterState.priorityFilterCount.toString() else null
            )
            FilterChip(
                label = "Status",
                selected = filterState.isStatusFilterActive,
                onSelected = { onStatusFilterClick() },
                badge = if (filterState.statusFilterCount > 0) filterState.statusFilterCount.toString() else null
            )
            FilterChip(
                label = "Due Date",
                selected = filterState.selectedDateRange != null,
                onSelected = { onDueDateFilterClick() },
                badge = if (filterState.dueDateFilterCount > 0) filterState.dueDateFilterCount.toString() else null
            )

            Button(
                enabled = true,
                style = ButtonStyle.TEXT,
                colorStyle = ColorStyle.DEFAULT,
                text = "Clear",
                modifier = Modifier.padding(end = 4.dp),
                onClick = onClearAllFilters
            )
        }
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
