package org.dhis2.community.workflow

data class WorkflowConfig(
    val teiCreatablePrograms: List<String>,
    val entityAutoCreation: List<EntityAutoCreationConfig> = emptyList(),
    val autoIncrementAttributes: List<AutoIncrementAttributes> = emptyList(),
    val programEnrollmentControl: List<ProgramEnrollmentControl> = emptyList(),
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
    val isDuplicationKey: Boolean = false
)

data class AutoIncrementAttributes(
    val programUid: String,
    val attributeUid: String,
)

data class ProgramEnrollmentControl(
    val programUid: String,
    val attributeUid: String,
    val attributeValue: String,
    val condition: String,
) {
    fun isConditionMet(attributeValue: String): Boolean {
        return when (condition) {
            "equals" -> attributeValue == this.attributeValue
            "not_equals" -> attributeValue != this.attributeValue
            "between" -> {
                val values = this.attributeValue.split(",").map { it.trim() }
                if (values.size != 2) return false
                val lowerBound = values[0].toDoubleOrNull() ?: return false
                val upperBound = values[1].toDoubleOrNull() ?: return false
                val value = attributeValue.toDoubleOrNull() ?: return false
                value in lowerBound..upperBound
            }
            "contains" -> this.attributeValue in attributeValue
            "not_contains" -> this.attributeValue !in attributeValue
            "greater_than" -> (attributeValue.toDoubleOrNull()
                ?: 0.0) > (this.attributeValue.toDoubleOrNull() ?: 0.0)
            "less_than" -> (attributeValue.toDoubleOrNull()
                ?: 0.0) < (this.attributeValue.toDoubleOrNull() ?: 0.0)
            else -> false
        }
    }
}