package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import timber.log.Timber
import java.util.Date


class CreationEvaluator (
    private val repository: TaskingRepository,
    private val d2: D2,
    /*private val orgUnitUid: String,
    //private val primaryAttributeUid: String,
    //private val secondaryAttributeUid: String,
    //private val tertiaryAttributeUid: String*/
) : TaskingEvaluator(d2, repository) {


    @RequiresApi(Build.VERSION_CODES.O)
    fun createTasks(
        taskProgramUid: String,
        taskTIETypeUid: String,
        targetProgramUid: String,
        sourceTieUid: String?,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String
    ): List<Task> {

        val results = evaluateForTie(sourceTieUid, targetProgramUid, sourceTieOrgUnitUid)
        if (results.isEmpty()) return emptyList()

        // 0) Preconditions & local metadata checks
        if (taskProgramUid.isBlank()) {
            Timber.e("CreationEvaluator: taskProgramUid is blank"); return emptyList()
        }
        if (taskTIETypeUid.isBlank()) {
            Timber.e("CreationEvaluator: taskTIETypeUid is blank"); return emptyList()
        }

        val tets = d2.trackedEntityModule().trackedEntityTypes().blockingGet()

        val programs = d2.programModule().programs().blockingGet()

        val tet = try {
            d2.trackedEntityModule().trackedEntityTypes().uid(taskTIETypeUid).blockingGet()
        } catch (e: Exception) {
            null
        }
        if (tet == null) {
            Timber.e("CreationEvaluator: TET $taskTIETypeUid not found locally. Sync metadata or fix config.")
            return emptyList()
        }

        val captureOus = repository.currentOrgUnits // user’s capture scope OUs

        val created = mutableListOf<Task>()

        results.forEach { result ->
            // 1) Resolve a safe OU to use for TEI creation
            val ouToUse = when {
                !result.orgUnit.isNullOrBlank() && captureOus.contains(result.orgUnit) -> result.orgUnit
                captureOus.contains(sourceTieOrgUnitUid) -> sourceTieOrgUnitUid
                captureOus.isNotEmpty() -> captureOus.first()
                else -> null
            }
            if (ouToUse == null) {
                Timber.e("CreationEvaluator: No capture-scope OU available for TEI creation"); return@forEach
            }

            // 2) Ensure we actually have required display info
            val tieAttrs = result.tieAttrs ?: run {
                Timber.e("CreationEvaluator: tieAttrs missing"); return@forEach
            }
            val dueDate = result.dueDate ?: run {
                Timber.e("CreationEvaluator: dueDate missing"); return@forEach
            }

            // 3) Create TEI (CATCH D2Error!)
            val newTeiUid = try {
                d2.trackedEntityModule().trackedEntityInstances().blockingAdd(
                    TrackedEntityInstanceCreateProjection.builder()
                        .organisationUnit(sourceTieOrgUnitUid)
                        .trackedEntityType(taskTIETypeUid)
                        .build()
                )
            } catch (e: D2Error) {
                Timber.tag("CreationEvaluator")
                    .e("TEI creation failed: code=${e.errorCode()} desc=${e.errorDescription()}")
                return@forEach
            }

            val (primary, secondary, tertiary) = tieAttrs


            val statusAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.statusUid ?:""
            val nameAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskNameUid?:""
            val priorityAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.priorityUid?:""
            val primaryAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskPrimaryAttrUid?:""
            val secondaryAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskSecondaryAttrUid?:""
            val tertiaryAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskTertiaryAttrUid?:""
            val dueDateAttrUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.dueDateUid?:""
            val taskSourceProgramUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskSourceProgramUid?: ""
            val taskSourceEnrollmentUid = repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskSourceEnrollmentUid?: ""

            repository.updateTaskAttrValue(statusAttrUid, "open", newTeiUid)
            repository.updateTaskAttrValue(nameAttrUid, result.taskingConfig.name, newTeiUid)
            repository.updateTaskAttrValue(priorityAttrUid, "high", newTeiUid)
            repository.updateTaskAttrValue(dueDateAttrUid, dueDate, newTeiUid)
            repository.updateTaskAttrValue(tertiaryAttrUid, tertiary, newTeiUid)
            repository.updateTaskAttrValue(secondaryAttrUid, secondary, newTeiUid)
            repository.updateTaskAttrValue(primaryAttrUid, primary, newTeiUid)
            repository.updateTaskAttrValue(taskSourceProgramUid, targetProgramUid, newTeiUid)
            repository.updateTaskAttrValue(taskSourceEnrollmentUid, sourceTieProgramEnrollment, newTeiUid)


            try {
                val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
                    EnrollmentCreateProjection.builder()
                        .trackedEntityInstance(newTeiUid)
                        .program(taskProgramUid)
                        .organisationUnit(sourceTieOrgUnitUid)
                        .build()
                )
                val today = Date()
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(today)
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(today)
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setStatus(EnrollmentStatus.ACTIVE)
            } catch (e: D2Error) {
                Timber.tag("CreationEvaluator")
                    .e("Enrollment failed: code=${e.errorCode()} desc=${e.errorDescription()}")
                return@forEach
            }



            created += Task(
                name = result.taskingConfig.name,
                description = result.taskingConfig.description,
                sourceProgramUid = taskSourceProgramUid,
                sourceProgramName = repository.getProgramName(result.programUid),
                teiUid = newTeiUid,
                teiPrimary = primary,
                teiSecondary = secondary,
                teiTertiary = tertiary,
                dueDate = dueDate,
                priority = result.taskingConfig.priority,
                status = "OPEN",
                sourceEnrollmentUid = sourceTieProgramEnrollment
            )
        }

        val msg = repository.getAllTasks(orgUnitUid = sourceTieOrgUnitUid.toString(), targetProgramUid)

        Timber.tag("FETCHED_TASKS").d(msg.toString())
        Timber.tag("CREATED_TASKS").d(created.toString())
        return created.toList()
    }

}

