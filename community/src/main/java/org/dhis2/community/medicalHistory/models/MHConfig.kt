package org.dhis2.community.medicalHistory.models

data class MedicalHistoryConfig(
    val medicalHistoryConfig: List<MedicalHistoryItem>,
    val baseProgram: List<BaseProgram>
){
    data class MedicalHistoryItem(
        val name: String,
        val source: List<Source>,
        val targetDE: String,
        val summary: SummaryConfig,
        val description: String?
    ){
        data class Source(
            val sourceProgramUid: String,
            val sourceDEs: List<String>,
            val sourceProgramName: String,
            val sourceProgramStageUid: String,
        )

        data class SummaryConfig(
            val type: SummaryType,
            val separator: String,
            val emptyValue: String,
            val format: String,
            val mappings: List<ValueMapping>,
            val rules: List<SummaryRule>?
        ){
            data class ValueMapping(
                val sourceValue: String,
                val targetValue: String
            )

            data class SummaryRule(
                val values: List<String>,
                val result: String,
                val condition: String?,
                val action: String?
            )

            enum class SummaryType {
                LIST,
                STATUS
            }
        }
    }

    data class BaseProgram(
        val baseProgramName: String,
        val baseProgramUid: String,
        val baseProgramStageUid: String,
    )
}



