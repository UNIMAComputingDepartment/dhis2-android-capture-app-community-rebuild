package org.dhis2.community.relationships

data class RelationshipConfig(
    val relationships: List<Relationship>
)

data class Relationship(
    val access: Access,
    val description: String,
    val maxCount: Int,
    val view: View,
    val relatedProgram: RelatedProgram,
    val attributeMappings: List<AttributeMapping> = emptyList(),
)

data class Access(
    val targetProgramUid: String,
    val targetRelationshipUid: String,
    val targetTeiTypeUid: String
)

data class View(
    val teiPrimaryAttribute: String,
    val teiSecondaryAttribute: String,
    val teiTertiaryAttribute: String
)

data class RelatedProgram(
    val programUid: String,
    val teiTypeUid: String,
    val teiTypeName: String
)

data class AttributeMapping(
    val sourceAttribute: String,
    val targetAttribute: String,
    val defaultValue: String?,
)
