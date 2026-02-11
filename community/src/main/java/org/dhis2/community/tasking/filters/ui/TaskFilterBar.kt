package org.dhis2.community.tasking.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.community.tasking.filters.models.FilterUiState
import org.dhis2.community.tasking.ui.TaskItemDefaults.programNameTextSize
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.FilterChip
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2TextStyle
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import org.hisp.dhis.mobile.ui.designsystem.theme.getTextStyle
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
    onClearAllFilters: () -> Unit,
    fontSize: Float = 20f
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filters",
                    tint = SurfaceColor.Primary,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    text = "Filters",
                    style = getTextStyle(DHIS2TextStyle.LABEL_LARGE).copy(fontSize = fontSize.sp),
                    color = TextColor.OnSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentWidth()
                )
            }
            Button(
                modifier = Modifier.padding(start = 2.dp),
                enabled = true,
                style = ButtonStyle.FILLED,
                onClick = onClearAllFilters,
                text = "Clear All",
                icon = {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Clear Filters"
                    )
                },
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Spacing10),
            verticalArrangement = Arrangement.spacedBy(-Spacing.Spacing4)
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
        }
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            thickness = 0.5.dp,
            color = TextColor.OnSurface.copy(alpha = 0.190f)
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