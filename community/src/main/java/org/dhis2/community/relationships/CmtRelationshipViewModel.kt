package org.dhis2.community.relationships

import androidx.compose.ui.graphics.vector.ImageVector

data class CmtRelationshipViewModel(
    val primaryAttribute: String,
    val secondaryAttribute: String?,
    val tertiaryAttribute: String?,
    val uid: String
)

data class CmtRelationshipTypeViewModel(
    val uid: String,
    val icon: ImageVector,
    val name: String,
    val description: String,
    val relatedTeis: List<CmtRelationshipViewModel>,
)
