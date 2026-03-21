package org.dhis2.mobile.aichat.ui.createchat

import org.dhis2.mobile.aichat.domain.model.SelectionItem
import org.dhis2.mobile.aichat.domain.model.SelectionPayload
import org.dhis2.mobile.aichat.domain.model.UserOrgUnit
import org.dhis2.mobile.aichat.domain.model.UserProgram

sealed interface CreateChatUiState {
    data object Loading : CreateChatUiState

    data class Form(
        val step: Int,
        val availableDataTypes: List<String>,
        val availablePeriods: List<String>,
        val availableItems: List<SelectionItem>,
        val availableOrgUnits: List<UserOrgUnit> = emptyList(),
        val availablePrograms: List<UserProgram> = emptyList(),
        val selectedDataType: String? = null,
        val selectedPeriod: String? = null,
        val selectedOrgUnitId: String? = null,
        val selectedOrgUnitName: String? = null,
        val includeChildren: Boolean = true,
        val canChangeOrgUnit: Boolean = true,
        val selectedProgramId: String? = null,
        val selectedItems: Set<SelectionItem> = emptySet(),
        val canSubmit: Boolean = false,
    ) : CreateChatUiState

    data class Error(
        val message: String,
    ) : CreateChatUiState

    data class Created(
        val chatId: String,
        val payload: SelectionPayload,
    ) : CreateChatUiState
}
