package org.dhis2.community.medicalHistory

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2

class Repository(
    private val d2: D2
){
    fun getMedicalHistoryConfigs(): MedicalHistoryConfig{
        val entries = d2.dataStoreModule().dataStore()
            .byNamespace()
            .eq("community_redesign")
            .blockingGet()

        return entries.firstOrNull{ it.key() == "medicalHistory"}
            ?.let{Gson().fromJson((it.value()), MedicalHistoryConfig::class.java)}
            ?: MedicalHistoryConfig(emptyList())
    }

    suspend fun updateImmunizationSummaryValues(
        eventUid: String,
        summaries: Map<String, String>
    ) = withContext(Dispatchers.IO){

        summaries.forEach { (targetDe, value) ->
            d2.trackedEntityModule().trackedEntityDataValues()
                .value(eventUid, targetDe)
                .blockingSet(value)
        }
    }
}