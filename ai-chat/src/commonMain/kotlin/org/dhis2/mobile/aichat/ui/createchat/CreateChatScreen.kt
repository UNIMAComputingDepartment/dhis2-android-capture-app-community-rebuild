package org.dhis2.mobile.aichat.ui.createchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.aichat.domain.model.SelectionItem
import org.dhis2.mobile.aichat.domain.model.UserProgram
import org.dhis2.mobile.aichat.ui.components.CenteredLoadingState
import org.dhis2.mobile.aichat.ui.components.formatDataTypeLabel
import org.dhis2.mobile.aichat.ui.components.formatPeriodLabel
import org.dhis2.mobile.aichat.ui.components.formatToken
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.Orientation
import org.hisp.dhis.mobile.ui.designsystem.component.RadioButtonBlock
import org.hisp.dhis.mobile.ui.designsystem.component.RadioButtonData
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing

@Composable
fun CreateChatScreen(
    uiState: CreateChatUiState,
    onDataTypeSelected: (String) -> Unit,
    onPeriodSelected: (String) -> Unit,
    onOrgUnitSelected: () -> Unit,
    onProgramSelected: (String) -> Unit,
    onToggleIncludeChildren: (Boolean) -> Unit,
    onToggleItem: (SelectionItem) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onCreateChat: () -> Unit,
) {
    DHIS2Theme {
        when (uiState) {
            CreateChatUiState.Loading -> CenteredLoadingState(message = "Creating chat...")
            is CreateChatUiState.Error -> Text(uiState.message, modifier = Modifier.padding(16.dp))
            is CreateChatUiState.Created -> Text("Created chat ${uiState.chatId}", modifier = Modifier.padding(16.dp))
            is CreateChatUiState.Form -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.Spacing16),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Spacing12),
                ) {
                    Text("Step ${uiState.step} of 5")
                    when (uiState.step) {
                        1 ->
                            OptionStep(
                                options = uiState.availableDataTypes,
                                selected = uiState.selectedDataType,
                                onSelected = onDataTypeSelected,
                                labelMapper = { value -> formatDataTypeLabel(value) },
                            )
                        2 ->
                            OptionStep(
                                options = uiState.availablePeriods,
                                selected = uiState.selectedPeriod,
                                onSelected = onPeriodSelected,
                                labelMapper = { value -> formatPeriodLabel(value) },
                            )
                        3 ->
                            OrgUnitStep(
                                selectedOrgUnitName = uiState.selectedOrgUnitName,
                                includeChildren = uiState.includeChildren,
                                canChangeOrgUnit = uiState.canChangeOrgUnit,
                                onOrgUnitSelected = onOrgUnitSelected,
                                onToggleIncludeChildren = onToggleIncludeChildren,
                            )
                        4 ->
                            ItemsStep(
                                options = uiState.availableItems,
                                selected = uiState.selectedItems,
                                selectedDataType = uiState.selectedDataType,
                                selectedProgramId = uiState.selectedProgramId,
                                availablePrograms = uiState.availablePrograms,
                                onProgramSelected = onProgramSelected,
                                onToggle = onToggleItem,
                            )
                        else -> ConfirmStep(uiState)
                    }
                    NavigationButtons(uiState.step, uiState.canSubmit, onPreviousStep, onNextStep, onCreateChat)
                }
            }
        }
    }
}

@Composable
private fun OptionStep(
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    labelMapper: @Composable (String) -> String,
) {
    val items =
        options.map { item ->
            RadioButtonData(
                uid = item,
                selected = item == selected,
                enabled = true,
                textInput = AnnotatedString(labelMapper(item)),
            )
        }

    RadioButtonBlock(
        orientation = Orientation.VERTICAL,
        content = items,
        itemSelected = items.find { it.selected },
        modifier = Modifier.fillMaxWidth(),
        onItemChange = { selectedItem -> onSelected(selectedItem.uid) },
    )
}

@Composable
private fun OrgUnitStep(
    selectedOrgUnitName: String?,
    includeChildren: Boolean,
    canChangeOrgUnit: Boolean,
    onOrgUnitSelected: () -> Unit,
    onToggleIncludeChildren: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Spacing8)) {
        Button(
            text = selectedOrgUnitName ?: "Select org unit",
            style = ButtonStyle.TONAL,
            enabled = canChangeOrgUnit,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOrgUnitSelected,
        )
        if (!canChangeOrgUnit && selectedOrgUnitName != null) {
            Text("Your assigned org unit is preselected.")
        }
        Row {
            Checkbox(checked = includeChildren, onCheckedChange = onToggleIncludeChildren)
            Text("Include child org units")
        }
    }
}

@Composable
private fun ItemsStep(
    options: List<SelectionItem>,
    selected: Set<SelectionItem>,
    selectedDataType: String?,
    selectedProgramId: String?,
    availablePrograms: List<UserProgram>,
    onProgramSelected: (String) -> Unit,
    onToggle: (SelectionItem) -> Unit,
) {
    val requiresProgram = selectedDataType == "tracker" || selectedDataType == "event"

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.Spacing8)) {
        if (requiresProgram) {
            item {
                Text("Program (required)")
            }
            if (availablePrograms.isEmpty()) {
                item {
                    Text("No programs available for your account.")
                }
            } else {
                item {
                    ProgramSelector(
                        programs = availablePrograms,
                        selectedProgramId = selectedProgramId,
                        onProgramSelected = onProgramSelected,
                    )
                }
            }
        }
        items(options) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(Spacing.Spacing12)) {
                    Checkbox(
                        checked = selected.contains(item),
                        onCheckedChange = { onToggle(item) },
                    )
                    Text(item.displayName ?: item.id)
                }
            }
        }
    }
}

@Composable
private fun ProgramSelector(
    programs: List<UserProgram>,
    selectedProgramId: String?,
    onProgramSelected: (String) -> Unit,
) {
    val items =
        programs.map { program ->
            RadioButtonData(
                uid = program.id,
                selected = program.id == selectedProgramId,
                enabled = true,
                textInput = AnnotatedString(program.displayName),
            )
        }

    RadioButtonBlock(
        orientation = Orientation.VERTICAL,
        content = items,
        itemSelected = items.find { it.selected },
        modifier = Modifier.fillMaxWidth(),
        onItemChange = { selected -> onProgramSelected(selected.uid) },
    )
}

@Composable
private fun ConfirmStep(state: CreateChatUiState.Form) {
    ListCard(
        modifier = Modifier.fillMaxWidth(),
        listCardState =
            rememberListCardState(
                title = ListCardTitleModel(text = "Selection summary"),
                additionalInfoColumnState =
                    rememberAdditionalInfoColumnState(
                        additionalInfoList =
                            listOf(
                                AdditionalInfoItem(key = "Data type", value = formatDataTypeLabel(state.selectedDataType)),
                                AdditionalInfoItem(key = "Period", value = formatPeriodLabel(state.selectedPeriod)),
                                AdditionalInfoItem(key = "Org unit", value = state.selectedOrgUnitName ?: "-"),
                                AdditionalInfoItem(key = "Items", value = state.selectedItems.size.toString()),
                            ),
                        syncProgressItem = AdditionalInfoItem(value = ""),
                    ),
            ),
        onCardClick = {},
    )
}

@Composable
private fun NavigationButtons(
    step: Int,
    canSubmit: Boolean,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onCreateChat: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Spacing8)) {
        Button(
            text = "Back",
            style = ButtonStyle.TONAL,
            enabled = step > 1,
            modifier = Modifier.weight(1f),
            onClick = onPreviousStep,
        )
        if (step < 5) {
            Button(
                text = "Next",
                modifier = Modifier.weight(1f),
                onClick = onNextStep,
            )
        } else {
            Button(
                text = "Create Chat",
                enabled = canSubmit,
                modifier = Modifier.weight(1f),
                onClick = onCreateChat,
            )
        }
    }
}
