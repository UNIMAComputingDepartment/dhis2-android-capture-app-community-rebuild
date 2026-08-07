package org.dhis2.community.workflow

private const val COMBINATION_AND = "and"

data class WorkflowConfig(
    val teiCreatablePrograms: List<String>,
    val entityAutoCreation: List<EntityAutoCreationConfig> = emptyList(),
    val autoIncrementAttributes: List<AutoIncrementAttributes> = emptyList(),
    val programEnrollmentControl: List<ProgramEnrollmentControl> = emptyList(),
    val autoEnrollment: List<AutoEnrollmentConfig> = emptyList(),
    val autoFillValues: List<AutoFillValuesConfig> = emptyList()
)

data class AutoFillValuesConfig(
    val triggerProgram: String,
    val targetProgram: String,
    val targetProgramStage: String,
    val targetElement: String,
    val updateExisting: Boolean? = false,
    val valueSource: ValueSource,
    val combination: String = COMBINATION_AND,
    val conditions: List<AutoFillValuesCondition> = emptyList(),
){
    data class AutoFillValuesCondition(
        val sourceType: String,
        val sourceUid: String,
        val condition: String,
        val value: String,
        val programStageUid: String? = null,
    )
    data class ValueSource(
        val valueSourceType: ValueSourceType,
        val uid: String? = null,
        val value: String? = null,
        val computation: ComputationConfig? = null,
    ){
        data class ComputationConfig(
            val computationType: ComputationType,
            val relationshipTypeUid: String,
            val relatedProgramUid: String,
            val programStageUid: String? = null,
            val dataElementUid: String? = null,
        ){
            enum class ComputationType{
                RELATED_TEI_COUNT
            }
        }
        enum class ValueSourceType{
            CONSTANT,
            TEI_ATTRIBUTE,
            EVENT_DATA_ELEMENT,
            COMPUTATION
        }
    }
}


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

data class AutoEnrollmentConfig(
    val triggerProgram: String,
    val targetProgram: String,
    val conditions: List<AutoEnrollmentCondition> = emptyList(),
    val combination: String = COMBINATION_AND,
)

data class AutoEnrollmentCondition(
    val sourceType: String,
    val sourceUid: String,
    val condition: String,
    val value: String,
    val programStageUid: String? = null,
)

data class ProgramEnrollmentControl(
    val programUid: String,
    val attributeUid: String,
    val attributeValue: String,
    val condition: String,
) {
    fun isConditionMet(attributeValue: String): Boolean {
        return evaluateWorkflowCondition(
            actualValue = attributeValue,
            expectedValue = this.attributeValue,
            condition = condition,
        )
    }
}

internal fun evaluateWorkflowCondition(
    actualValue: String?,
    expectedValue: String,
    condition: String,
): Boolean {
    val actual = actualValue?.trim() ?: return false
    val expected = expectedValue.trim()

    return when (condition.lowercase()) {
        "equals" -> actual == expected
        "not_equals" -> actual != expected
        "between" -> {
            val values = expected.split(",").map { it.trim() }
            if (values.size != 2) return false
            val lowerBound = values[0].toDoubleOrNull() ?: return false
            val upperBound = values[1].toDoubleOrNull() ?: return false
            val value = actual.toDoubleOrNull() ?: return false
            value in lowerBound..upperBound
        }
        "contains" -> expected in actual
        "not_contains" -> expected !in actual
        "greater_than" -> {
            val actualNumber = actual.toDoubleOrNull() ?: return false
            val expectedNumber = expected.toDoubleOrNull() ?: return false
            actualNumber > expectedNumber
        }
        "less_than" -> {
            val actualNumber = actual.toDoubleOrNull() ?: return false
            val expectedNumber = expected.toDoubleOrNull() ?: return false
            actualNumber < expectedNumber
        }
        else -> false
    }
}