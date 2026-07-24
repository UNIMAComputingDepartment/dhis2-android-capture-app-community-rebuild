package org.dhis2.community.relationships

import androidx.annotation.DrawableRes

data class CmtRelationshipViewModel(
    val primaryAttribute: String,
    val secondaryAttribute: String?,
    val tertiaryAttribute: String?,
    val uid: String,
    val iconName: String,
    val programUid: String,
    val enrollmentUid: String,
    val isHead: Boolean = false
)

data class CmtRelationshipTypeViewModel(
    val uid: String,
    //val icon: String,
    val name: String,
    val description: String,
    val relatedProgramName: String,
    val relatedProgramUid: String,
    val relatedTeis: List<CmtRelationshipViewModel>,
    val maxCount: Int = Int.MAX_VALUE,
    val supportsHeadPromotion: Boolean = false
) {

}
