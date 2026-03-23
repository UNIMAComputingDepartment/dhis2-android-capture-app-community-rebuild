package org.dhis2.community.medicalHistory.models

data class MedicalHistoryConfig(
    val medicalHistoryConfig: List<MedicalHistoryItem>,
    val baseProgram: List<BaseProgram>
){
    data class MedicalHistoryItem(
        val name: String,
        val source: List<Source>,
        val targetDE: String
    ){
        data class Source(
            val sourceProgramUid: String,
            val sourceDEs: List<String>,
            val sourceProgramName: String,
            val sourceProgramStageUid: String,
        )
    }

    data class BaseProgram(
        val baseProgramName: String,
        val baseProgramUid: String,
        val baseProgramStageUid: String,
    )
}



