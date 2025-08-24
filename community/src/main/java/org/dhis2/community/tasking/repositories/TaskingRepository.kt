package org.dhis2.community.tasking.repositories

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class TaskingRepository(
    private val d2: org.hisp.dhis.android.core.D2,
) {
    private val taskStorage = mutableListOf<Task>()

    fun getTaskingConfig(): TaskingConfig{
        val entries = d2.dataStoreModule()
            .dataStore()
            .byNamespace()
            .eq("community_redesign")
            .blockingGet()

        return entries.firstOrNull { it.key() == "tasking" }
            ?.let { Gson().fromJson(it.value(), TaskingConfig::class.java) }
            ?: TaskingConfig(emptyList())
    }

    fun evaluateCondition(
        condition: TaskingConfig.TaskConfig.Condition,
        tieUid: String
    ): Boolean {

        val lhsValue = resolvedReference(condition.lhs, tieUid)
        val rhsValue = condition.rhs.value

        return when (condition.op) {
            "EQUALS" -> lhsValue == rhsValue
            "NOT_EQUALS" -> lhsValue != rhsValue
            else -> false
        }
    }

    private fun resolvedReference(
        ref: TaskingConfig.TaskConfig.Reference,
        tieUid: String
    ): Any? {
        return when (ref.type) {
            "attribute" -> d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tieUid)
                .byTrackedEntityAttribute().eq(ref.uid)
                .one().blockingGet()?.value()

            "constant" -> ref.value
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateDueDate(
        taskConfig: TaskingConfig.TaskConfig
    ): String {
        val anchorDate = LocalDate.now()
        val dueDate = anchorDate.plusDays(taskConfig.period.dueIn.days.toLong())

        return dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun getTieAttributes(
        tieUid: String,
        tieView: TaskingConfig.TaskConfig.TeiView
    ): Triple<String, String, String> {
        fun getAttr(uid: String) =
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tieUid)
                .byTrackedEntityAttribute().eq(uid)
                .one().blockingGet()?.value() ?: ""

        return Triple(
            getAttr(tieView.teiPrimaryAttribute),
            getAttr(tieView.teiSecondaryAttribute),
            getAttr(tieView.teiTertiaryAttribute)
        )
    }

    fun getProgramName(programUid: String): String {
        return d2.programModule().programs()
            .uid(programUid)
            .blockingGet()?.displayName() ?: ""
    }

    fun getTieByType(
        trackedEntityTypeUid: String,
        orgUnitUid: String,
        programUid: String
    ) : List<TrackedEntityInstance> {
        var query = d2.trackedEntityModule().trackedEntityInstances()
            .byTrackedEntityType().eq(trackedEntityTypeUid)

        if (programUid.isNotEmpty()) {
            query = query.byProgramUids(listOf(programUid))
        }

        if(orgUnitUid.isNotEmpty()) {
            query = query.byOrganisationUnitUid().eq(orgUnitUid)
        }

        return query.blockingGet()
    }

    fun filterTiesByAttributes(
        ties : List<TrackedEntityInstance>,
        attributeUid: String,
        attributeValue: String
    ): List<TrackedEntityInstance> {
        return ties.filter { tie ->
            val attrValue = d2.trackedEntityModule()
                .trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tie.uid())
                .byTrackedEntityAttribute().eq(attributeUid)
                .blockingGet()
                .firstOrNull()
                ?.value()

            attrValue == attributeValue
        }
    }
}