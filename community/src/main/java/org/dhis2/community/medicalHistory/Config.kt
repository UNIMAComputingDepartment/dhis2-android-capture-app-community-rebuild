package org.dhis2.community.medicalHistory

data class MedicalHistoryConfig(
    val medicalHistoryConfig: List<MedicalHistoryItem>
){
    data class MedicalHistoryItem(
        val chronicalConditions: MHConfigs,
        val immunization: MHConfigs
    ){
        data class MHConfigs(
            val sourceDes: List<String>,
            val sourcePrograms: List<String>,
            val targetDe: String
        )
    }
}



