package org.dhis2.community.relationships

data class CmtRelationshipViewModel(
    val primaryAttribute: String,
    val secondaryAttribute: String?,
    val tertiaryAttribute: String?,
    val uid: String,
    val programUid: String,
    val enrollmentUid: String
)

data class CmtRelationshipTypeViewModel(
    val uid: String,
    //val icon: ImageVector,
    val name: String,
    val description: String,
    val relatedProgramName: String,
    val relatedProgramUid: String,
    val relatedTeis: List<CmtRelationshipViewModel>,
)
