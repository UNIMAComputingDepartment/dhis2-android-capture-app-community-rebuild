package org.dhis2.community.relationships

data class CmtRelationshipViewModel(
    val primaryAttribute: String,
    val secondaryAttribute: String?,
    val tertiaryAttribute: String?,
    val uid: String
)
