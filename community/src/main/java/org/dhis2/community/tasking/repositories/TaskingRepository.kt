package org.dhis2.community.tasking.repositories

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import io.reactivex.subjects.PublishSubject
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntityInstanceQueryScopeOrderColumn.attribute
import timber.log.Timber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Singleton

@Singleton
class TaskingRepository(
    private val d2: D2,
) {

    private val dataElementChangedSubject = PublishSubject.create<String>()
    fun observeDataElementChanges(): io.reactivex.Observable<String> =
        dataElementChangedSubject.hide()

    private fun notifyDataElementChanged(dataElement: String) {
        dataElementChangedSubject.onNext(dataElement)
    }

    private var cachedConfig: TaskingConfig? = null

    val taskStatusAttributeUid =
        getCachedConfig()?.taskProgramConfig?.firstOrNull()?.statusUid ?: ""

    fun getCachedConfig() = cachedConfig
    fun getTaskingConfig(): TaskingConfig {
        cachedConfig?.let { return it }

        val entries = d2.dataStoreModule().dataStore()
            .byNamespace().eq("community_redesign")
            .blockingGet()

        val config = entries.firstOrNull { it.key() == "tasking" }
            ?.let { Gson().fromJson(it.value(), TaskingConfig::class.java) }
            ?: TaskingConfig(
                programTasks = emptyList(),
                taskProgramConfig = emptyList()
            )

        cachedConfig = config
        return config
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateDueDate(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String
    ): String? {
        val enrollment = getLatestEnrollment(teiUid, programUid) ?: return null

        val anchorDate = enrollment.incidentDate() ?: enrollment.enrollmentDate() ?: return null
        val localDate = anchorDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val dueDate = localDate.plusDays(taskConfig.period.dueInDays.toLong())

        Timber.d("DueDate for TEI=${teiUid} task=${taskConfig.name} is $dueDate")

        return dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun getLatestEnrollment(teiUid: String, programUid: String): Enrollment? {
        val v = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .one()
            .blockingGet()
        //.maxByOrNull { it.enrollmentDate()?.time ?: 0 }

        return v
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


    fun getTaskTei(
        orgUnitUid: String
    ): List<TrackedEntityInstance> {
        val taskTeiUid = cachedConfig?.taskProgramConfig?.firstOrNull()?.teiTypeUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        return d2.trackedEntityModule().trackedEntityInstances().byTrackedEntityType()
            .eq(taskTeiUid)
            .blockingGet()
    }

    fun getAllTasks(
//        tieTypeUid: String,
        orgUnitUid: String,
        thisProgramUid: String,
    ): List<Task> {
        val teis = getTaskTei(orgUnitUid)

        // Find the config for this program
        val programConfig = getCachedConfig()?.taskProgramConfig?.firstOrNull()
        val taskConfig = getCachedConfig()?.programTasks?.firstOrNull() {it.programUid == thisProgramUid}
        //taskingConfig.taskConfigs
        //.firstOrNull { it.programUid == programUid }

        return teis.map { tei ->
            Task(
                name = tei.getAttributeValue(programConfig?.taskNameUid) ?: "Unnamed Task",
                description = tei.getAttributeValue(programConfig?.description) ?: "",
                sourceProgramUid = tei.getAttributeValue(programConfig?.taskSourceProgramUid) ?: "",
                sourceEnrollmentUid = tei.getAttributeValue(programConfig?.taskSourceEnrollmentUid) ?: "",
                sourceProgramName = programConfig?.programName ?: "",
                teiUid = tei.uid(),
                teiPrimary = tei.getAttributeValue(taskConfig?.teiView?.teiPrimaryAttribute) ?: "",
                teiSecondary = tei.getAttributeValue(taskConfig?.teiView?.teiSecondaryAttribute) ?: "",
                teiTertiary = tei.getAttributeValue(taskConfig?.teiView?.teiTertiaryAttribute) ?: "",
                dueDate = tei.getAttributeValue(programConfig?.dueDateUid) ?: "",
                priority = tei.getAttributeValue(programConfig?.priorityUid) ?: "Normal",
                status = tei.getAttributeValue(programConfig?.statusUid) ?: "OPEN"
            )
        }
    }

    fun TrackedEntityInstance.getAttributeValue(attributeUid: String?): String? {
        if (attributeUid.isNullOrEmpty()) return null
        return attribute(attributeUid)
            ?.value()
    }


    fun updateTaskAttrValue(taskAttrUid: String?, newTaskAttrValue: String, taskTieUid: String) {
        if (taskAttrUid != null)
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(taskAttrUid, taskTieUid)
                .blockingSet(newTaskAttrValue)
    }

    val currentOrgUnits = d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(
        OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
        .blockingGet().map { it.uid() }

    fun getAllTrackedEntityInstances(
        programUid: String,
        sourceTieUid: String?,
        sourceTieOrgUnit: String
    ): List<TrackedEntityInstance> {
        val enrollments = d2.enrollmentModule().enrollments()
            .byOrganisationUnit().eq(sourceTieOrgUnit)
            .byProgram().eq(programUid)
            .byTrackedEntityInstance().eq(sourceTieUid)
            .blockingGet()

        return enrollments.mapNotNull { uid ->
            d2.trackedEntityModule().trackedEntityInstances()
                .uid(uid.trackedEntityInstance())
                .blockingGet()
        }
    }
}
