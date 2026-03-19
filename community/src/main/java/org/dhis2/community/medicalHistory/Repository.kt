package org.dhis2.community.medicalHistory

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2
import timber.log.Timber

class Repository(
    private val d2: D2
){
    fun getMedicalHistoryConfigs(): MedicalHistoryConfig{
        val entries = d2.dataStoreModule().dataStore()
            .byNamespace()
            .eq("community_redesign")
            .blockingGet()

        val rawValue = entries.firstOrNull { it.key() == "medicalHistory" }?.value()
            ?: return MedicalHistoryConfig(emptyList())

        if (rawValue.isBlank()) return MedicalHistoryConfig(emptyList())

        val value = if (rawValue.startsWith("JsonWrapper(json=")) {
            rawValue.removePrefix("JsonWrapper(json=").removeSuffix(")")
        } else {
            rawValue
        }

        if (!value.trim().startsWith("{")) return MedicalHistoryConfig(emptyList())

        return try {
            Gson().fromJson(value, MedicalHistoryConfig::class.java)
        } catch (exception: Exception) {
            Timber.e(exception, "Error parsing medicalHistory config")
            MedicalHistoryConfig(emptyList())
        }
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