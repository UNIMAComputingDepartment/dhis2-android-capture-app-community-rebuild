package org.dhis2.community.medicalHistory.models

data class MedicalHistoryConfig(
    val medicalHistoryConfig: List<MedicalHistoryItem>,
    val baseProgram: List<BaseProgram>
) {
    data class MedicalHistoryItem(
        val name: String,
        val source: List<Source>,
        val targetDE: String,
        val summary: SummaryConfig,
        val description: String?
    ) {
        data class Source(
            val sourceProgramUid: String,
            val sourceDEs: List<SourceDataElement>,
            val sourceProgramName: String,
            val sourceProgramStageUid: String,
        ) {
            data class SourceDataElement(
                val uid: String,
                val label: String? = null,
                val summaryGroup: String? = null
            )
        }

        data class SummaryConfig(
            val type: SummaryType,
            val separator: String,
            val emptyValue: String,
            val format: String,
            val mappings: List<ValueMapping>? = null,
            val display: DisplayConfig? = null,
            val rules: List<SummaryRule>? = null,
            val evaluation: EvaluationConfig? = null
        ) {

            data class EvaluationConfig(
                val strategy: Strategy,
                val trueValues: List<String> = emptyList(),
                val falseValues: List<String> = emptyList()
            )
            data class ValueMapping(
                val sourceValue: String,
                val targetValue: String
            )

            data class DisplayConfig(
                val visibleItems: Int,
                val overflowFormat: String
            )

            data class SummaryRule(
                val priority: Int = 0,
                val values: List<String>,
                val result: String,
                val condition: String? = null,
                val action: String? = null
            )

            enum class SummaryType {
                LIST,
                STATUS,
                CONDITION_LIST
            }

            enum class Strategy {
                ALL_TRUE,
                ANY_TRUE
            }
        }

    }

    data class BaseProgram(
        val baseProgramName: String,
        val baseProgramUid: String,
        val baseProgramStageUid: String,
    )
}



