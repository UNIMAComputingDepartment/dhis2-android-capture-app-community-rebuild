package org.dhis2.community.tasking.repositories

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import timber.log.Timber
import java.util.Date
import javax.inject.Singleton

@Singleton
class TaskingRepository(
    internal val d2: D2,
) {

    private var cachedConfig: TaskingConfig? = null

    val taskStatusAttributeUid =
        getTaskingConfig().taskProgramConfig.firstOrNull()?.statusUid ?: ""
    val taskProgressAttributeUid =
        getTaskingConfig().taskProgramConfig.firstOrNull()?.taskProgressUid ?: ""

    private val programDisplayNames = mutableMapOf<String, String?>()

    fun getCachedConfig() = cachedConfig

    init {
        CoroutineScope(Dispatchers.IO).launch {
            getTaskingConfig()
        }
    }

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

    fun getOrgUnit(taskTeiUid: String): String? {
        val tei = d2.trackedEntityModule().trackedEntityInstances()
            .uid(taskTeiUid)
            .blockingGet()
        return tei?.organisationUnit()
    }

    fun getLatestEnrollment(teiUid: String, programUid: String): Enrollment? {
        val enrollment = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .one()
            .blockingGet()
        //.maxByOrNull { it.enrollmentDate()?.time ?: 0 }

        return enrollment
    }

    fun getProgramName(programUid: String): String {
        return d2.programModule().programs()
            .uid(programUid)
            .blockingGet()?.displayName() ?: ""
    }

    fun getSourceProgramIcon(sourceProgramUid: String): String? {
        val program = d2.programModule().programs()
            .uid(sourceProgramUid).blockingGet()

        return program?.style()?.icon()
    }

    fun getSourceProgramColor(sourceProgramUid: String): String? {
        val program = d2.programModule().programs()
            .uid(sourceProgramUid).blockingGet()

        return program?.style()?.color()
    }


    fun getTaskTei(): List<TrackedEntityInstance> {
        val taskTeiUid = cachedConfig?.taskProgramConfig?.firstOrNull()?.teiTypeUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        val programUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid

        val activeEnrollments = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .blockingGet()

        val activeTeiUids: Collection<String>? =
            activeEnrollments.map { it.trackedEntityInstance() as String }

        if (activeTeiUids.isNullOrEmpty()) return emptyList()

        return d2.trackedEntityModule().trackedEntityInstances()
            .byUid().`in`(activeTeiUids)
            .withTrackedEntityAttributeValues()
            .orderByLastUpdated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
    }

    fun getTasks(): List<Task> {
        val teis = getTaskTei()

        val programConfig = getCachedConfig()?.taskProgramConfig?.firstOrNull()

        return teis.map { tei ->
            teiToTask(tei, programConfig)
        }
    }

    fun getAllTasks(): List<Task> {
        val taskTeiUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.teiTypeUid
        val programUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        val activeEnrollments = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .blockingGet()

        val activeTeiUids: Collection<String>? =
            activeEnrollments.map { it.trackedEntityInstance() as String }

        if (activeTeiUids.isNullOrEmpty()) return emptyList()

        val allTies = d2.trackedEntityModule().trackedEntityInstances()
            .byUid().`in`(activeTeiUids)
            .withTrackedEntityAttributeValues()
            .orderByLastUpdated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()

        val programConfig = getCachedConfig()?.taskProgramConfig?.firstOrNull()

        return allTies.map { tei ->
            teiToTask(tei, programConfig)
        }

    }

    fun TrackedEntityInstance.getAttributeValue(attributeUid: String?): String? {
        if (attributeUid.isNullOrEmpty()) return null
        return this.trackedEntityAttributeValues()
            ?.firstOrNull { it.trackedEntityAttribute() == attributeUid }
            ?.value()
    }

    private fun teiToTask(
        tei: TrackedEntityInstance,
        programConfig: TaskingConfig.TaskProgramConfig?
    ): Task {
        return Task(
            name = tei.getAttributeValue(programConfig?.taskNameUid) ?: "Unnamed Task",
            description = tei.getAttributeValue(programConfig?.description) ?: "",
            sourceProgramUid = tei.getAttributeValue(programConfig?.taskSourceProgramUid) ?: "",
            sourceEnrollmentUid = tei.getAttributeValue(programConfig?.taskSourceEnrollmentUid)
                ?: "",
            sourceProgramName = programConfig?.programName ?: "",
            sourceTeiUid = tei.getAttributeValue(programConfig?.taskSourceTeiUid) ?: "",
            teiUid = tei.uid(),
            teiPrimary = tei.getAttributeValue(programConfig?.taskPrimaryAttrUid) ?: "",
            teiSecondary = tei.getAttributeValue(programConfig?.taskSecondaryAttrUid) ?: "",
            teiTertiary = tei.getAttributeValue(programConfig?.taskTertiaryAttrUid) ?: "",
            dueDate = tei.getAttributeValue(programConfig?.dueDateUid) ?: "",
            priority = tei.getAttributeValue(programConfig?.priorityUid) ?: "Normal",
            iconNane = getSourceProgramIcon(
                sourceProgramUid = (tei.getAttributeValue(programConfig?.taskSourceProgramUid)
                    ?: "")
            ),
            status = tei.getAttributeValue(programConfig?.statusUid) ?: "OPEN",
            sourceEventUid = tei.getAttributeValue(programConfig?.taskSourceEventUid) ?: "",
            progress = tei.getAttributeValue(programConfig?.taskProgressUid)?.toFloatOrNull() ?: 0f
        )
    }


    fun updateTaskAttrValue(taskAttrUid: String?, newTaskAttrValue: String?, taskTieUid: String) {
        if (taskAttrUid != null)
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(taskAttrUid, taskTieUid)
                .blockingSet(newTaskAttrValue ?: "")
    }

    val currentOrgUnits = d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(
        OrganisationUnit.Scope.SCOPE_DATA_CAPTURE
    )
        .blockingGet().map { it.uid() }

    fun getProgramDisplayName(programUid: String): String? {
        programDisplayNames[programUid]?.let { return it }
        return try {
            programDisplayNames[programUid] = d2.programModule().programs()
                .byUid().eq(programUid).one()
                .blockingGet()?.displayName()
            programDisplayNames[programUid]
        } catch (e: Exception) {
            Timber.tag("TaskingRepository")
                .e(e, "Error fetching program display name for UID: $programUid")
            null
        }
    }


    fun createTask(
        task: Task,
        sourceTeiOrgUnitUid: String,
        taskTEITypeUid: String,
        taskProgramUid: String
    ): Boolean {
        val newTeiUid = try {
            d2.trackedEntityModule().trackedEntityInstances().blockingAdd(
                TrackedEntityInstanceCreateProjection.builder()
                    .organisationUnit(sourceTeiOrgUnitUid)
                    .trackedEntityType(taskTEITypeUid)
                    .build()
            )
        } catch (e: D2Error) {
            Timber.tag("CreationEvaluator")
                .e("TEI creation failed: code=${e.errorCode()} desc=${e.errorDescription()}")
            return false
        }

        val taskProgramConfig = this.getTaskingConfig().taskProgramConfig.firstOrNull()
        this.updateTaskAttrValue(taskProgramConfig?.statusUid ?: "", "open", newTeiUid)
        this.updateTaskAttrValue(taskProgramConfig?.taskNameUid ?: "", task.name, newTeiUid)
        this.updateTaskAttrValue(taskProgramConfig?.priorityUid ?: "", task.priority, newTeiUid)
        this.updateTaskAttrValue(taskProgramConfig?.dueDateUid ?: "", task.dueDate, newTeiUid)
        this.updateTaskAttrValue(
            taskProgramConfig?.taskTertiaryAttrUid ?: "",
            task.teiTertiary,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSecondaryAttrUid ?: "",
            task.teiSecondary,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskPrimaryAttrUid ?: "",
            task.teiPrimary,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceProgramUid ?: "",
            task.sourceProgramUid,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceEnrollmentUid ?: "",
            task.sourceEnrollmentUid,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceTeiUid ?: "",
            task.sourceTeiUid,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceEventUid ?: "",
            task.sourceEventUid,
            newTeiUid
        )

        try {
            val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
                EnrollmentCreateProjection.builder()
                    .trackedEntityInstance(newTeiUid)
                    .program(taskProgramUid)
                    .organisationUnit(sourceTeiOrgUnitUid)
                    .build()
            )
            val today = Date()
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(today)
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(today)
            d2.enrollmentModule().enrollments().uid(enrollmentUid)
                .setStatus(EnrollmentStatus.ACTIVE)
            return true
        } catch (e: D2Error) {
            Timber.tag("CreationEvaluator")
                .e("Enrollment failed: code=${e.errorCode()} desc=${e.errorDescription()}")
            return false
        }
    }

    fun getLatestEvent(
        programUid: String,
        dataElementUid: String,
        enrollmentUd: String,
        eventUid: String?
    ): Event? {
        val stage = d2.programModule().programStages().byProgramUid()
            .eq(programUid).blockingGet()
            .firstOrNull {
                d2.programModule().programStageDataElements()
                    .byProgramStage().eq(it.uid())
                    .byDataElement().eq(dataElementUid)
                    .blockingGet().isNotEmpty()
            } ?: return null

        val events = if (eventUid == null)
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollmentUd)
                .byProgramStageUid().eq(stage.uid())
                .withTrackedEntityDataValues()
                .blockingGet()
        else
            d2.eventModule().events()
                .byUid().eq(eventUid)
                .withTrackedEntityDataValues()
                .blockingGet()

        return events
            .maxByOrNull {
                it.created() ?: it.eventDate() ?: Date(0)
            }
    }

    fun getTasksForPgEnrollment(enrollmentUID: String): List<Task> {
        return getAllTasks().filter { it.sourceEnrollmentUid == enrollmentUID }
    }

    fun getEnrollment(enrollmentUid: String) =
        d2.enrollmentModule().enrollments().uid(enrollmentUid).blockingGet()
}