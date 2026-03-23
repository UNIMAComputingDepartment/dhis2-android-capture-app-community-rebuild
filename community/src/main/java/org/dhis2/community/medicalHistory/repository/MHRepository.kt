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
            val entries = d2.dataStoreModule()
                .dataStore()
                .byNamespace()
                .eq("community_redesign")
                .blockingGet()

            val entry = entries.firstOrNull { it.key() == "medicalHistory" }
            val rawValue = entry?.value()

            if (rawValue.isNullOrBlank()) {
                return MedicalHistoryConfig(
                    medicalHistoryConfig = emptyList(),
                    baseProgram = emptyList()
                )
            }

            // 🔥 Clean JSON (handles multiple cases)
            val cleanedValue = when {
                rawValue.startsWith("JsonWrapper(json=") ->
                    rawValue.removePrefix("JsonWrapper(json=").removeSuffix(")")

                rawValue.startsWith("\"") ->
                    Gson().fromJson(rawValue, String::class.java) // unwrap string

                else -> rawValue
            }

            // Optional safety check
            if (!cleanedValue.trim().startsWith("{")) {
                return MedicalHistoryConfig(
                    medicalHistoryConfig = emptyList(),
                    baseProgram = emptyList()
                )
            }

            val config = Gson().fromJson(cleanedValue, MedicalHistoryConfig::class.java)

            // 🔥 Defensive safety (in case fields are missing in JSON)
            MedicalHistoryConfig(
                medicalHistoryConfig = config.medicalHistoryConfig ?: emptyList(),
                baseProgram = config.baseProgram ?: emptyList()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            MedicalHistoryConfig(
                medicalHistoryConfig = emptyList(),
                baseProgram = emptyList()
            )
        }
    }

    suspend fun updateSummaryValues(
        teiUid: String,
        baseProgramUid: String,
        summaries: Map<String, String>
    ) = withContext(Dispatchers.IO) {

        val baseProgramStageUid = getMedicalHistoryConfigs().baseProgram.firstOrNull()?.baseProgramStageUid

        val eventUid = getLatestEvent(
            teiUid = teiUid,
            programUid = baseProgramUid,
            programStageUid = baseProgramStageUid
        )?.uid().toString()

        val repository = d2.trackedEntityModule().trackedEntityDataValues()

        summaries.forEach { (deUid, newValue) ->

            val currentValue = repository.value(eventUid, deUid).blockingGet()

            print("the current value : $currentValue")
                repository.value(eventUid, deUid).blockingSet(newValue)

        }
    }


    private fun getLatestEvent(
        teiUid: String,
        programUid: String,
        programStageUid: String? = null
    ): Event? {
        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()

        if(enrollments.isEmpty()) return null

        val enrollmentUids = enrollments.map { it.uid() }

        val events = d2.eventModule().events()
            .byEnrollmentUid().`in`(enrollmentUids)
            .byProgramStageUid().eq(programStageUid)
            .blockingGet()
        return events
            .filter { it.eventDate() != null || it.created() != null }
            .maxByOrNull { it.eventDate() ?: it.created()!! }
    }

    fun getLatestValueFromProgramDeep(
        teiUid: String,
        sourceProgramUid: String,
        deUid: String,
        sourceProgramStageUid: String?
    ): String? {

        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(sourceProgramUid)
            .blockingGet()

        if (enrollments.isEmpty()) return null

        val enrollmentUids = enrollments.map { it.uid() }

        val events = d2.eventModule().events()
            .byEnrollmentUid().`in`(enrollmentUids)
            .byProgramStageUid().eq(sourceProgramStageUid)
            .blockingGet()

        return events
            // Pair each event with its matching DE value (if exists)
            .mapNotNull { event ->
                val date = event.eventDate() ?: event.created() ?: return@mapNotNull null

                val value = event.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == deUid }
                    ?.value()

                if (value.isNullOrBlank()) return@mapNotNull null

                date to value
            }
            // 🔥 Sort by latest date
            .sortedByDescending { it.first }
            // 🔥 Remove duplicate values (keep latest occurrence)
            .distinctBy { it.second }
            // Take the latest unique value
            .firstOrNull()
            ?.second
    }

    /*fun getLatestValueFromProgram(
        teiUid: String,
        programUid: String,
        deUid: String
    ) : String?{


        val dataValues = getLatestEvent(teiUid, programUid, )?.trackedEntityDataValues() ?: return null

        return dataValues.firstOrNull{ it.dataElement() == deUid }?.value()
    }
    */

    fun getDataElementDisplayName(
        deUid: String
    ): String {
        return d2.dataElement(deUid)
            ?.displayFormName().toString()
    }



}