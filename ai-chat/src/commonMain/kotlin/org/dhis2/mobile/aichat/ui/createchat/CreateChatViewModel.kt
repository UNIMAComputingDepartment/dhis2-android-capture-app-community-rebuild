package org.dhis2.mobile.aichat.ui.createchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.dhis2.mobile.aichat.domain.model.OrgUnitSelection
import org.dhis2.mobile.aichat.domain.model.SelectionItem
import org.dhis2.mobile.aichat.domain.model.SelectionPayload
import org.dhis2.mobile.aichat.domain.model.UserOrgUnit
import org.dhis2.mobile.aichat.domain.repository.CurrentUserProvider
import org.dhis2.mobile.aichat.domain.usecase.CreateChatInput
import org.dhis2.mobile.aichat.domain.usecase.CreateChatSessionUseCase

class CreateChatViewModel(
    private val createChatSessionUseCase: CreateChatSessionUseCase,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val initialState =
        CreateChatUiState.Form(
            step = 1,
            availableDataTypes = listOf("aggregate", "indicator", "programIndicator", "event", "tracker"),
            availablePeriods = listOf("LAST_12_MONTHS", "THIS_YEAR", "LAST_YEAR", "LAST_6_MONTHS", "LAST_4_QUARTERS", "THIS_QUARTER"),
            availableItems = emptyList(),
        )

    private val _uiState = MutableStateFlow<CreateChatUiState>(initialState)
    val uiState = _uiState.asStateFlow()

    init {
        loadAvailableOrgUnits()
    }

    fun onDataTypeSelected(value: String) {
        updateForm {
            copy(
                selectedDataType = value,
                selectedProgramId = if (requiresProgram(value)) selectedProgramId else null,
            )
        }
        if (requiresProgram(value)) {
            loadAvailablePrograms()
        }
    }

    fun onPeriodSelected(value: String) = updateForm { copy(selectedPeriod = value) }

    fun onProgramSelected(value: String) =
        updateForm {
            copy(selectedProgramId = value.ifBlank { null })
        }

    fun onOrgUnitSelected(id: String, name: String) =
        updateForm {
            copy(
                selectedOrgUnitId = id,
                selectedOrgUnitName = name,
            )
        }

    fun onToggleIncludeChildren(value: Boolean) = updateForm { copy(includeChildren = value) }

    fun onItemsLoaded(items: List<SelectionItem>) = updateForm { copy(availableItems = items) }

    fun onItemToggled(item: SelectionItem) =
        updateForm {
            val mutable = selectedItems.toMutableSet()
            if (!mutable.add(item)) {
                mutable.remove(item)
            }
            copy(selectedItems = mutable)
        }

    fun nextStep() = updateForm { copy(step = (step + 1).coerceAtMost(5)) }

    fun previousStep() = updateForm { copy(step = (step - 1).coerceAtLeast(1)) }

    fun createChat() {
        val form = (_uiState.value as? CreateChatUiState.Form) ?: return
        val dataType = form.selectedDataType ?: return
        val period = form.selectedPeriod ?: return
        val orgUnitId = form.selectedOrgUnitId ?: return
        val orgUnitName = form.selectedOrgUnitName ?: return

        viewModelScope.launch {
            _uiState.value = CreateChatUiState.Loading
            val payload =
                SelectionPayload(
                    dataType = dataType,
                    period = period,
                    orgUnit =
                        OrgUnitSelection(
                            id = orgUnitId,
                            displayName = orgUnitName,
                            includeChildOrgUnits = form.includeChildren,
                        ),
                    selectedItems = form.selectedItems.toList(),
                    programId = form.selectedProgramId,
                )
            val username = currentUserProvider.username()
            createChatSessionUseCase(
                CreateChatInput(
                    username = username,
                    selectionPayload = payload,
                ),
            ).fold(
                onSuccess = {
                    _uiState.value = CreateChatUiState.Created(it.id, payload)
                },
                onFailure = {
                    _uiState.value = CreateChatUiState.Error(it.message ?: "Unable to create chat")
                },
            )
        }
    }

    fun shouldOpenOrgUnitSelector(): Boolean {
        val form = _uiState.value as? CreateChatUiState.Form ?: return false
        return form.availableOrgUnits.size > 1
    }

    private fun updateForm(block: CreateChatUiState.Form.() -> CreateChatUiState.Form) {
        val state = _uiState.value as? CreateChatUiState.Form ?: return
        val updated = block(state)

        val programValid =
            if (requiresProgram(updated.selectedDataType)) {
                !updated.selectedProgramId.isNullOrBlank()
            } else {
                true
            }

        val itemValid = updated.availableItems.isEmpty() || updated.selectedItems.isNotEmpty()

        _uiState.value =
            updated.copy(
                canSubmit =
                    updated.selectedDataType != null &&
                        updated.selectedPeriod != null &&
                        updated.selectedOrgUnitId != null &&
                        programValid &&
                        itemValid,
            )
    }

    private fun requiresProgram(dataType: String?): Boolean =
        dataType == "tracker" || dataType == "event"

    private fun loadAvailableOrgUnits() {
        viewModelScope.launch {
            runCatching { currentUserProvider.captureOrgUnits() }
                .onSuccess { orgUnits -> applyOrgUnits(orgUnits) }
                .onFailure {
                    updateForm { copy(availableOrgUnits = emptyList(), canChangeOrgUnit = true) }
                }
        }
    }

    private fun applyOrgUnits(orgUnits: List<UserOrgUnit>) {
        updateForm {
            if (orgUnits.size == 1) {
                val onlyOrgUnit = orgUnits.first()
                copy(
                    availableOrgUnits = orgUnits,
                    selectedOrgUnitId = onlyOrgUnit.id,
                    selectedOrgUnitName = onlyOrgUnit.displayName,
                    canChangeOrgUnit = false,
                )
            } else {
                copy(
                    availableOrgUnits = orgUnits,
                    canChangeOrgUnit = orgUnits.size > 1,
                )
            }
        }
    }

    private fun loadAvailablePrograms() {
        val currentForm = _uiState.value as? CreateChatUiState.Form ?: return
        if (currentForm.availablePrograms.isNotEmpty()) return

        viewModelScope.launch {
            runCatching { currentUserProvider.availablePrograms() }
                .onSuccess { programs ->
                    updateForm {
                        val stillValidSelection = programs.any { it.id == selectedProgramId }
                        copy(
                            availablePrograms = programs,
                            selectedProgramId = if (stillValidSelection) selectedProgramId else null,
                        )
                    }
                }.onFailure {
                    updateForm { copy(availablePrograms = emptyList(), selectedProgramId = null) }
                }
        }
    }
}
