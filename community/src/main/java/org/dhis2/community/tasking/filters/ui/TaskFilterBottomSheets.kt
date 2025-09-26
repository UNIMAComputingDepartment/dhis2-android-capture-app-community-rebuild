package org.dhis2.community.tasking.filters.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.dhis2.community.tasking.filters.models.DateRangeFilter
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.component.state.BottomSheetShellUIState
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import timber.log.Timber


@Composable
fun ProgramFilterBottomSheet(
    programs: List<CheckBoxData>,
    onDismiss: () -> Unit,
    onApplyFilters: (List<CheckBoxData>) -> Unit
) {
    Timber.d("ProgramFilterBottomSheet rendered with ${programs.size} programs")
    MultiSelectBottomSheet(
        items = programs,
        title = "Filter by Program",
        noResultsFoundString = "No programs found",
        searchToFindMoreString = "Search to find more programs",
        doneButtonText = "Done",
        onItemsSelected = { selected ->
            Timber.d("ProgramFilterBottomSheet applied filters: $selected")
            onApplyFilters(selected)
        },
        onDismiss = {
            Timber.d("ProgramFilterBottomSheet dismissed")
            onDismiss()
        }
    )
}

@Composable
fun PriorityFilterBottomSheet(
    priorities: List<CheckBoxData>,
    onDismiss: () -> Unit,
    onApplyFilters: (List<CheckBoxData>) -> Unit
) {
    Timber.d("PriorityFilterBottomSheet rendered with ${priorities.size} priorities")
    MultiSelectBottomSheet(
        items = priorities,
        title = "Filter by Priority",
        noResultsFoundString = "No priorities found",
        searchToFindMoreString = "Search to find more priorities",
        doneButtonText = "Done",
        onItemsSelected = { selected ->
            Timber.d("PriorityFilterBottomSheet applied filters: $selected")
            onApplyFilters(selected)
        },
        onDismiss = {
            Timber.d("PriorityFilterBottomSheet dismissed")
            onDismiss()
        }
    )
}

@Composable
fun StatusFilterBottomSheet(
    statuses: List<CheckBoxData>,
    onDismiss: () -> Unit,
    onApplyFilters: (List<CheckBoxData>) -> Unit
) {
    Timber.d("StatusFilterBottomSheet rendered with ${statuses.size} statuses")
    MultiSelectBottomSheet(
        items = statuses,
        title = "Filter by Status",
        noResultsFoundString = "No statuses found",
        searchToFindMoreString = "Search to find more statuses",
        doneButtonText = "Done",
        onItemsSelected = { selected ->
            Timber.d("StatusFilterBottomSheet applied filters: $selected")
            onApplyFilters(selected)
        },
        onDismiss = {
            Timber.d("StatusFilterBottomSheet dismissed")
            onDismiss()
        }
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewDueDateFilterBottomSheet() {
    DueDateFilterBottomSheet(
        selectedRange = null,
        onDismiss = {},
        onApplyFilters = {}
    )
}

@Composable
fun DueDateFilterBottomSheet(
    selectedRange: DateRangeFilter?,
    onDismiss: () -> Unit,
    onApplyFilters: (DateRangeFilter?) -> Unit
) {
    Timber.d("DueDateFilterBottomSheet rendered with selectedRange: $selectedRange")
    val dateRangeLabels = mapOf(
        DateRangeFilter.Today to "Today",
        DateRangeFilter.Yesterday to "Yesterday",
        DateRangeFilter.Tomorrow to "Tomorrow",
        DateRangeFilter.ThisWeek to "This Week",
        DateRangeFilter.LastWeek to "Last Week",
        DateRangeFilter.NextWeek to "Next Week",
        DateRangeFilter.ThisMonth to "This Month",
        DateRangeFilter.LastMonth to "Last Month",
        DateRangeFilter.NextMonth to "Next Month"
    )
    val dateRanges = DateRangeFilter.entries
    var selected by remember { mutableStateOf(selectedRange) }

    val radioButtonItems = dateRanges.map { range ->
        RadioButtonData(
            uid = range.name,
            selected = selected == range,
            enabled = true,
            textInput = AnnotatedString(
                dateRangeLabels[range]
                    ?: range.name.replace(Regex("([A-Z])"), " $1").trim()
            )
        )
    }

    BottomSheetShell(
        uiState = BottomSheetShellUIState(
            title = "Filter by Due Date",
            headerTextAlignment = TextAlign.Center,
            showTopSectionDivider = true,
            showBottomSectionDivider = true,
            contentPadding = PaddingValues(horizontal = Spacing.Spacing24, vertical = 0.dp)
        ),
        content = {
            Surface(
                color = SurfaceColor.ContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (radioButtonItems.isEmpty()) {
                    Text(
                        text = "No due date options available",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    RadioButtonBlock(
                        orientation = Orientation.VERTICAL,
                        content = radioButtonItems,
                        itemSelected = radioButtonItems.find { it.selected },
                        modifier = Modifier.fillMaxWidth(),
                        onItemChange = { item ->
                            selected = if (selected?.name == item.uid) null
                            else DateRangeFilter.valueOf(item.uid)
                        }
                    )
                }
            }
        },
        buttonBlock = {
            ButtonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
                primaryButton = {
                    Button(
                        enabled = true,
                        style = ButtonStyle.FILLED,
                        colorStyle = ColorStyle.DEFAULT,
                        text = "Done",
                        icon = { Icon(Icons.Filled.Check, contentDescription = "Done") },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onApplyFilters(selected) }
                    )
                },
                secondaryButton = null
            )
        },
        onDismiss = onDismiss
    )
}