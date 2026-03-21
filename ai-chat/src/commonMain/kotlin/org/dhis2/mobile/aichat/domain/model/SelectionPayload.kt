package org.dhis2.mobile.aichat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SelectionPayload(
    val dataType: String,
    val period: String,
    val orgUnit: OrgUnitSelection,
    val selectedItems: List<SelectionItem>,
    val programId: String? = null,
)

@Serializable
data class OrgUnitSelection(
    val id: String,
    val displayName: String,
    val includeChildOrgUnits: Boolean,
)

@Serializable
data class SelectionItem(
    val id: String,
    val displayName: String? = null,
)

