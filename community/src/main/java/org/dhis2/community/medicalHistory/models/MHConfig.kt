package org.dhis2.community.medicalHistory.models

data class MedicalHistoryConfig(
    val medicalHistoryConfig: List<MedicalHistoryItem>
){
    data class MedicalHistoryItem(
        val name: String,
        val source: List<Source>,
        val targetDE: String
    ){
        data class Source(
            val sourceProgramUid: String,
            val sourceDEs: List<String>,
        )
    }
}



