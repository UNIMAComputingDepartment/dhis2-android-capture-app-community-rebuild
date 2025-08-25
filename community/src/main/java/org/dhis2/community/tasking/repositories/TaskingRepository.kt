package org.dhis2.community.tasking.repositories

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskingRepository(
    private val d2: D2,
) {

    private var cachedConfig: TaskingConfig? = null

    private val statusAttributeUid: String by lazy {
        getTaskingConfig().taskConfigs
            .firstOrNull()?.completion?.condition?.args?.filter
            ?.firstOrNull { it.lhs.ref == "teiAttribute" && it.lhs.fn == "status" }?.lhs?.uid
            ?: ""
    }

    fun getTaskingConfig(): TaskingConfig {
        cachedConfig?.let { return it }

        val entries = d2.dataStoreModule().dataStore()
            .byNamespace().eq("community_redesign")
            .blockingGet()

        val config = entries.firstOrNull { it.key() == "tasking" }
            ?.let { Gson().fromJson(it.value(), TaskingConfig::class.java) }
            ?: TaskingConfig(
                taskConfigs = emptyList(),
                taskProgramConfig = emptyList()
            )

        cachedConfig = config
        return config
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateDueDate(
        taskConfig: TaskingConfig.TaskConfig,
        teiUid: String,
        programUid: String
    ): String? {
        val enrollment = getLatestEnrollment(teiUid, programUid) ?: return null

        val anchorDate = enrollment.incidentDate() ?: enrollment.enrollmentDate() ?: return null
        val localDate = anchorDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val dueDate = localDate.plusDays(taskConfig.period.dueIn.days.toLong())

        return dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun evaluateCondition(
        condition: TaskingConfig.TaskConfig.Condition,
        teiUid: String
    ): Boolean {
        val lhsValue = resolvedReference(condition.lhs, teiUid)
        val rhsValue = condition.rhs.value

        return when (condition.op) {
            "EQUALS" -> lhsValue == rhsValue
            "NOT_EQUALS" -> lhsValue != rhsValue
            else -> false
        }
    }

  private fun resolvedReference(
        ref: TaskingConfig.TaskConfig.Reference,
        teiUid: String,
        programUid: String? = null
    ): Any? {
        return when (ref.ref) {
            "teiAttribute" -> {
                ref.uid?.let { uid ->
                    d2.trackedEntityModule().trackedEntityAttributeValues()
                        .byTrackedEntityInstance().eq(teiUid)
                        .byTrackedEntityAttribute().eq(uid)
                        .one().blockingGet()?.value()
                }
            }

            "eventData" -> {
                if (programUid == null || ref.uid == null) return null

                val enrollment = getLatestEnrollment(teiUid, programUid) ?: return null
                val events = d2.eventModule().events()
                    .byEnrollmentUid().eq(enrollment.uid())
                    .byProgramStageUid().eq(ref.uid)
                    .withTrackedEntityDataValues()
                    .blockingGet()

                events.firstOrNull()?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == ref.uid }
                    ?.value()
            }

            "static" -> ref.value?.toString()
            else -> null
        }
    }


    fun getLatestEnrollment(teiUid: String, programUid: String): Enrollment? {
        return d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()
            .maxByOrNull { it.enrollmentDate()?.time ?: 0 }
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
    ): List<TrackedEntityInstance> {
        var query = d2.trackedEntityModule().trackedEntityInstances()
            .byTrackedEntityType().eq(trackedEntityTypeUid)

        if (programUid.isNotEmpty()) query = query.byProgramUids(listOf(programUid))
        if (orgUnitUid.isNotEmpty()) query = query.byOrganisationUnitUid().eq(orgUnitUid)

        return query.blockingGet()
    }

   fun getAllTasks(
        tieTypeUid: String,
        orgUnitUid: String,
        programUid: String
    ): List<TrackedEntityInstance> {
        return getTieByType(tieTypeUid, orgUnitUid, programUid)
    }

 fun filterTiesByAttributes(
        ties: List<TrackedEntityInstance>,
        attributeUid: String,
        attributeValue: String
    ): List<TrackedEntityInstance> {
        return ties.filter { tie ->
            val attrValue = d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tie.uid())
                .byTrackedEntityAttribute().eq(attributeUid)
                .blockingGet()
                .firstOrNull()
                ?.value()
            attrValue == attributeValue
        }
    }

   fun getTaskStatus(tieUid: String): String {
        if (statusAttributeUid.isEmpty()) return ""
        return d2.trackedEntityModule().trackedEntityAttributeValues()
            .byTrackedEntityInstance().eq(tieUid)
            .byTrackedEntityAttribute().eq(statusAttributeUid)
            .one().blockingGet()?.value() ?: ""
    }

  fun updateTaskStatus(taskTieUid: String, newStatus: String) {
        if (statusAttributeUid.isEmpty()) return
        d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(statusAttributeUid, taskTieUid)
            .blockingSet(newStatus)
    }
}
