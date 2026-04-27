package org.dhis2.community.medicalHistory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2

class Summaries (
    private val d2: D2
){
    suspend fun buildImmunizationSummaries(
        teiUid: String,
        repository: Repository
    ): Map<String, String> = withContext(Dispatchers.IO){
        val config = repository.getMedicalHistoryConfigs()
        val summaries = mutableMapOf<String, String>()

        config.medicalHistoryConfig.forEach { item ->
            val immunization =  item.immunization
            val collected = mutableListOf<String>()

            val size = minOf(
                immunization.sourcePrograms.size,
                immunization.sourceDes.size
            )

            for (i in 0 until size) {
                val programUid = immunization.sourcePrograms[i]
                val deUid = immunization.sourceDes[i]

                val value = getLatestValueFromProgram(teiUid, programUid, deUid)
                if (!value.isNullOrBlank()){
                    collected.add(value)
                }
            }

            val summaryText = if (collected.isEmpty()){
                "None recorded"
            }else{
                collected.distinct().joinToString(",")
            }
            summaries[immunization.targetDe] = summaryText
        }
            summaries
    }

    private fun getLatestValueFromProgram(
        teiUid: String,
        programUid: String,
        deUid: String
    ) : String?{
        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()

        if(enrollments.isEmpty()) return null

        val enrollmentUids = enrollments.map { it.uid() }

        val events = d2.eventModule().events()
            .byEnrollmentUid().`in`(enrollmentUids)
            .blockingGet()

        val latest = events
            .filter { it.eventDate() != null || it.created() != null }
            .maxByOrNull { it.eventDate() ?: it.created()!! }

        val dvs = latest?.trackedEntityDataValues() ?: return null

        return dvs.firstOrNull{ it.dataElement() == deUid }?.value()
    }
}