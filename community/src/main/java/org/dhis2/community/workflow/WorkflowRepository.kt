package org.dhis2.community.workflow

import com.google.gson.Gson
import org.dhis2.community.relationships.RelationshipConfig

class WorkflowRepository(
    private val d2: org.hisp.dhis.android.core.D2
) {

    fun getWorkflowConfig(): WorkflowConfig {
        val entries = d2.dataStoreModule()
            .dataStore()
            .byNamespace()
            .eq("community_redesign")
            .blockingGet()

        return entries.firstOrNull { it.key() == "workflow" }
            ?.let { Gson().fromJson(it.value(), WorkflowConfig::class.java) }
            ?: WorkflowConfig(emptyList())
    }

}