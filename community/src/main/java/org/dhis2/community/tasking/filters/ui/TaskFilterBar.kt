package org.dhis2.community.tasking.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.dhis2.community.tasking.filters.models.FilterUiState
import org.hisp.dhis.mobile.ui.designsystem.component.FilterChip
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import timber.log.Timber

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow (
            modifier = Modifier
                //.horizontalScroll(rememberScrollState())
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
        IconButton(
            modifier = Modifier
                .padding(start = 2.dp)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterVertically),
            enabled = true,
            //style = ButtonStyle.FILLED,
            colors = IconButtonColors(
                containerColor = if (filterState.isAnyFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (filterState.isAnyFilterActive) TextColor.OnPrimary else TextColor.OnSurfaceVariant,
                disabledContainerColor = Color.Gray,
                disabledContentColor = TextColor.OnSurfaceVariant.copy(alpha = 0.38f)
            ),
            onClick = onClearAllFilters
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear Filters"
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
