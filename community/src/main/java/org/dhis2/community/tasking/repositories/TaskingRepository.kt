package org.dhis2.community.tasking.repositories

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntityInstanceQueryScopeOrderColumn.attribute
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskingRepository(
    private val d2: D2,
) {

    private var cachedConfig: TaskingConfig? = null

    val statusAttributeUid: String by lazy {
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

    fun getLatestEnrollment(teiUid: String, programUid: String): Enrollment? {
        return d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()
            .maxByOrNull { it.enrollmentDate()?.time ?: 0 }
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
        programUid: String,
    ): List<Task> {
        val teis = getTieByType(tieTypeUid, orgUnitUid, programUid)

        // Find the config for this program
        val programConfig = getTaskingConfig().taskProgramConfig.firstOrNull()
        val attr = getTaskingConfig().taskConfigs.firstOrNull()
            //taskingConfig.taskConfigs
            //.firstOrNull { it.programUid == programUid }

        return teis.map { tei ->
            Task(
                name = tei.getAttributeValue(programConfig?.name) ?: "Unnamed Task",
                description = tei.getAttributeValue(programConfig?.description) ?: "",
                programUid = programUid,
                programName = programConfig?.programName ?: "",
                teiUid = tei.uid(),
                teiPrimary = tei.getAttributeValue(attr?.teiView?.teiPrimaryAttribute) ?: "",
                teiSecondary = tei.getAttributeValue(attr?.teiView?.teiSecondaryAttribute) ?: "",
                teiTertiary = tei.getAttributeValue(attr?.teiView?.teiTertiaryAttribute) ?: "",
                dueDate = tei.getAttributeValue(programConfig?.dueDate) ?: "",
                priority = tei.getAttributeValue(programConfig?.priority) ?: "Normal",
                status = tei.getAttributeValue(programConfig?.status) ?: "OPEN"
            )
        }
    }

    // Same helper as before
    fun TrackedEntityInstance.getAttributeValue(attributeUid: String?): String? {
        if (attributeUid.isNullOrEmpty()) return null
        return attribute(attributeUid)
            ?.value()

        /**
         * trackedEntityAttributeValues()
            ?.firstOrNull { it.trackedEntityAttribute() == attributeUid }
            ?.value()
        **/
    }

    fun updateTaskStatus(taskTieUid: String, newStatus: String) {
        if (statusAttributeUid.isEmpty()) return
        d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(statusAttributeUid, taskTieUid)
            .blockingSet(newStatus)
    }
}
