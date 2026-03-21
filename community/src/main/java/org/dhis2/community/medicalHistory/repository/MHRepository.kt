package org.dhis2.community.medicalHistory.repository

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.commons.bindings.dataElement
import org.dhis2.community.medicalHistory.models.MedicalHistoryConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.Event

class MHRepository(
    private val d2: D2
){
    fun getMedicalHistoryConfigs(): MedicalHistoryConfig {
        return try {
            val entries = d2.dataStoreModule().dataStore()
                .byNamespace()
                .eq("community_redesign")
                .blockingGet()

            val entry = entries.firstOrNull { it.key() == "medicalHistory" }

            val rawValue = entry?.value()

            if (rawValue.isNullOrBlank()) {
                return MedicalHistoryConfig(emptyList())
            }

            // 🔥 Handle JsonWrapper (same as workflow)
            val value = if (rawValue.startsWith("JsonWrapper(json=")) {
                rawValue.removePrefix("JsonWrapper(json=").removeSuffix(")")
            } else {
                rawValue
            }

            // Optional safety check
            if (!value.trim().startsWith("{")) {
                return MedicalHistoryConfig(emptyList())
            }

            Gson().fromJson(value, MedicalHistoryConfig::class.java)

        } catch (e: Exception) {
            e.printStackTrace()
            MedicalHistoryConfig(emptyList())
        }
    }

    suspend fun updateSummaryValues(
        teiUid: String,
        programUid: String,
        summaries: Map<String, String>
    ) = withContext(Dispatchers.IO) {

        val eventUid = getLatestEvent(teiUid, programUid)?.uid().toString()

        summaries.forEach { (targetDe, value) ->
            d2.trackedEntityModule().trackedEntityDataValues()
                .value(eventUid, targetDe)
                .blockingSet(value)
        }
    }


    private fun getLatestEvent(
        teiUid: String,
        programUid: String
    ): Event? {
        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()

        if(enrollments.isEmpty()) return null

        val enrollmentUids = enrollments.map { it.uid() }

        val events = d2.eventModule().events()
            .byEnrollmentUid().`in`(enrollmentUids)
            .blockingGet()
        return events
            .filter { it.eventDate() != null || it.created() != null }
            .maxByOrNull { it.eventDate() ?: it.created()!! }
    }

    fun getLatestValueFromProgram(
        teiUid: String,
        programUid: String,
        deUid: String
    ) : String?{

        val dataValues = getLatestEvent(teiUid, programUid)?.trackedEntityDataValues() ?: return null

        return dataValues.firstOrNull{ it.dataElement() == deUid }?.value()
    }

    fun getDataElementDisplayName(
        deUid: String
    ): String {
        return d2.dataElement(deUid)
            ?.displayFormName().toString()
    }



}