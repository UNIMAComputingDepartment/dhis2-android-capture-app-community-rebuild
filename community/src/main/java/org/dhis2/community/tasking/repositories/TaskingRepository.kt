package org.dhis2.community.tasking.repositories

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.hisp.dhis.android.core.event.Event
import java.time.chrono.ChronoLocalDate
import java.util.Date


class TaskingRepository(
    private val d2: org.hisp.dhis.android.core.D2,
) {

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

    private fun getFollowUpDataElementValue(
        teiUid: String,
        programUid: String,
        stageUid: String,
        dataElementUid: String
    ): String? {
        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()

        val events = enrollments.flatMap { enrollment ->
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollment.uid())
                .byProgramStageUid().eq(stageUid)
                .withTrackedEntityDataValues()
                .blockingGet()
        }

        val event = events.firstOrNull() ?: return null

        return event.trackedEntityDataValues()
            ?.firstOrNull { it.dataElement() == dataElementUid }
            ?.value()
    }

    fun evaluateCondition(
        condition: TaskingConfig.TaskConfig.Condition,
        tieUid: String
    ): Boolean {

        val lhsValue = resolvedReference(condition.lhs, tieUid,)
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
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byTrackedEntityAttribute().eq(ref.uid!!)
                    .one().blockingGet()
                    ?.value()
            }

            "eventData" -> {
                if (programUid == null || ref.uid == null) return null

                val enrollments = d2.enrollmentModule().enrollments()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byProgram().eq(programUid)
                    .blockingGet()

                val events = enrollments.flatMap { enrollment ->
                    d2.eventModule().events()
                        .byEnrollmentUid().eq(enrollment.uid())
                        .byProgramStageUid().eq(ref.uid) // follow-up stage
                        .withTrackedEntityDataValues()
                        .blockingGet()
                }

                val event = events.firstOrNull()
                event?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == ref.uid }  // ref.uid = data element UID
                    ?.value()
            }
            "static" -> ref.value?.toString()
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateDueDate(
        taskConfig: TaskingConfig.TaskConfig,
        teiUid: String,
        programUid: String
    ): String? {

        val enrollment = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()
            .firstOrNull()
            ?: throw IllegalArgumentException("No enrollment found for TEI $teiUid in program $programUid")

        val anchorDate = enrollment.incidentDate() ?: enrollment.enrollmentDate()
            ?: throw IllegalArgumentException("No valid date found for enrollment ${enrollment.uid()}")

        val localDate = anchorDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val dueDate = localDate.plusDays(taskConfig.period.dueIn.days.toLong())

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
    private val statusAttributeUid = getTaskingConfig().taskConfigs
        .firstOrNull()?.completion?.condition?.args?.filter
        ?.firstOrNull { it.lhs.ref == "teiAttribute" && it.lhs.fn == "status" }?.lhs?.uid
        ?: ""

    fun updateTaskStatus(taskTieUid: String, newStatus : String){
        d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(statusAttributeUid, taskTieUid)
            .blockingSet(newStatus)
    }

    fun getAllTasks(
        tieTypeUid: String,
        orgUnitUid: String,
        programUid: String
    ) : List<TrackedEntityInstance> {
        return d2.trackedEntityModule().trackedEntityInstances()
            .byTrackedEntityType().eq(tieTypeUid)
            .byOrganisationUnitUid().eq(orgUnitUid)
            .byProgramUids(listOf(programUid))
            .blockingGet()
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

    fun getTaskStatus(tieUid: String): String {
        return d2.trackedEntityModule().trackedEntityAttributeValues()
            .byTrackedEntityInstance().eq(tieUid)
            .byTrackedEntityAttribute().eq(statusAttributeUid)
            .one().blockingGet()
            ?.value() ?: ""
    }
}