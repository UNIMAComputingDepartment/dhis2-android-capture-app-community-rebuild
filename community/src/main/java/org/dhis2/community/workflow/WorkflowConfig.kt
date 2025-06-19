package org.dhis2.community.workflow

data class WorkflowConfig(
    val teiCreatablePrograms: List<String>,
    val entityAutoCreation: List<EntityAutoCreationConfig> = emptyList(),
)

data class EntityAutoCreationConfig(
    val triggerProgram: String,
    val targetProgram: String,
    val targetTeiType: String,
    val relationshipType: String,
    val attributesMappings: List<AttributeMapping> = emptyList(),
)

data class AttributeMapping(
    val sourceAttribute: String,
    val targetAttribute: String,
    val defaultValue: String?,
)