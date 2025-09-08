package org.dhis2.community.tasking.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.dhis2.community.tasking.filters.models.DateRangeFilter
import org.hisp.dhis.mobile.ui.designsystem.component.*
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.tooling.preview.Preview

val Spacing0 = 0.dp
val Spacing24 = 24.dp

object InternalSizeValues {
    val Size386 = 386.dp
}

data class BottomSheetShellUIState(
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val searchQuery: String? = null,
    val showTopSectionDivider: Boolean = true,
    val showBottomSectionDivider: Boolean = true,
    val bottomPadding: androidx.compose.ui.unit.Dp = Spacing0,
    val headerTextAlignment: TextAlign = TextAlign.Center,
    val scrollableContainerMinHeight: androidx.compose.ui.unit.Dp = Spacing0,
    val scrollableContainerMaxHeight: androidx.compose.ui.unit.Dp = InternalSizeValues.Size386,
    val animateHeaderOnKeyboardAppearance: Boolean = true,
    val contentPadding: PaddingValues = PaddingValues(horizontal = Spacing24)
)

@Composable
fun BottomSheetShell(
    uiState: BottomSheetShellUIState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    buttonBlock: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Column(modifier = modifier) {
        content()
        buttonBlock?.invoke()
    }
}

@Composable
fun ProgramFilterBottomSheet(
    programs: List<CheckBoxData>,
    onDismiss: () -> Unit,
    onApplyFilters: (List<CheckBoxData>) -> Unit
) {
    MultiSelectBottomSheet(
        items = programs,
        title = "Filter by Program",
        noResultsFoundString = "No programs found",
        searchToFindMoreString = "Search to find more programs",
        doneButtonText = "Done",
        onItemsSelected = onApplyFilters,
        onDismiss = onDismiss
    )
}

@Composable
fun OrgUnitFilterBottomSheet(
    orgUnits: List<OrgTreeItem>,
    selectedOrgUnits: Set<String>,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit = {},
    onItemSelected: (String, Boolean) -> Unit = { _, _ -> },
    onApplyFilters: () -> Unit
) {
    OrgBottomSheet(
        orgTreeItems = orgUnits,
        title = "Filter by Organisation Unit",
        headerTextAlignment = TextAlign.Start,
        onSearch = onSearch,
        onDismiss = onDismiss,
        onItemClick = { uid ->
            val checked = !selectedOrgUnits.contains(uid)
            onItemSelected(uid, checked)
        },
        onItemSelected = onItemSelected,
        onClearAll = {},
        onDone = onApplyFilters
    )
}

@Composable
fun PriorityFilterBottomSheet(
    priorities: List<CheckBoxData>,
    onDismiss: () -> Unit,
    onApplyFilters: (List<CheckBoxData>) -> Unit
) {
    MultiSelectBottomSheet(
        items = priorities,
        title = "Filter by Priority",
        noResultsFoundString = "No priorities found",
        searchToFindMoreString = "Search to find more priorities",
        doneButtonText = "Done",
        onItemsSelected = onApplyFilters,
        onDismiss = onDismiss
    )
}

@Composable
fun StatusFilterBottomSheet(
    statuses: List<CheckBoxData>,
    onDismiss: () -> Unit,
    onApplyFilters: (List<CheckBoxData>) -> Unit
) {
    MultiSelectBottomSheet(
        items = statuses,
        title = "Filter by Status",
        noResultsFoundString = "No statuses found",
        searchToFindMoreString = "Search to find more statuses",
        doneButtonText = "Done",
        onItemsSelected = onApplyFilters,
        onDismiss = onDismiss
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
    val dateRangeLabels = mapOf(
        DateRangeFilter.Today to "Today",
        DateRangeFilter.Yesterday to "Yesterday",
        DateRangeFilter.ThisWeek to "This Week",
        DateRangeFilter.LastWeek to "Last Week",
        DateRangeFilter.ThisMonth to "This Month",
        DateRangeFilter.LastMonth to "Last Month"
    )
    val dateRanges = DateRangeFilter.entries.filter { it != DateRangeFilter.Custom }
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
        onDismiss = onDismiss,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top
            ) {
                RadioButtonBlock(
                    orientation = Orientation.VERTICAL,
                    content = radioButtonItems,
                    itemSelected = radioButtonItems.find { it.selected },
                    onItemChange = { item ->
                        selected = if (selected?.name == item.uid) null
                        else DateRangeFilter.valueOf(item.uid)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
        }
    )
}
