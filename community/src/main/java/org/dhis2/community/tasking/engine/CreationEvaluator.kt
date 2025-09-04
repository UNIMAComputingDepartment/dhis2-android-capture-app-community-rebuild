package org.dhis2.community.tasking.engine

import android.os.Build
import android.util.Log
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
        sourceTieOrgUnitUid: String
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
                        .organisationUnit(ouToUse)
                        .trackedEntityType(taskTIETypeUid)
                        .build()
                )
            } catch (e: D2Error) {
                Timber.tag("CreationEvaluator")
                    .e("TEI creation failed: code=${e.errorCode()} desc=${e.errorDescription()}")
                return@forEach
            }

            // 4) Enroll it (also safe-guarded)
            try {
                val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
                    EnrollmentCreateProjection.builder()
                        .trackedEntityInstance(newTeiUid)
                        .program(taskProgramUid)
                        .organisationUnit(ouToUse)
                        .build()
                )
                val today = Date()
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(today)
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(today)
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setStatus(EnrollmentStatus.ACTIVE)
            } catch (e: D2Error) {
                Timber.tag("CreationEvaluator")
                    .e("Enrollment failed: code=${e.errorCode()} desc=${e.errorDescription()}")
                // Optional: clean up TEI if you want to avoid orphans
                // d2.trackedEntityModule().trackedEntityInstances().uid(newTeiUid).blockingDelete()
                return@forEach
            }

            val (primary, secondary, tertiary) = tieAttrs

            created += Task(
                name = result.taskingConfig.name,
                description = result.taskingConfig.description,
                programUid = result.programUid,
                programName = repository.getProgramName(result.programUid),
                teiUid = newTeiUid,
                teiPrimary = primary,
                teiSecondary = secondary,
                teiTertiary = tertiary,
                dueDate = dueDate,
                priority = result.taskingConfig.priority,
                status = "OPEN"
            )
        }

        Timber.tag("CREATED_TASKS").d(created.toString())
        return created.toList()
    }


//    @RequiresApi(Build.VERSION_CODES.O)
//    fun createTasks(taskProgramUid: String,
//                    taskTIETypeUid: String,
//                    targetProgramUid: String,
//                    sourceTieUid: String?,
//                    sourceTieOrgUnitUid: String
//                    /*result: EvaluationResult*/
//    ): List<Task> {
//
//        val evaluatedResult = evaluateForTie(
//            sourceTieUid,
//            targetProgramUid,
//            sourceTieOrgUnitUid,
//        )
//
//        val tasks = mutableListOf<Task>()
//        evaluatedResult.forEach { result ->
//            if (result.tieAttrs == null || result.dueDate == null) return emptyList()
//
//        val (primary, secondary, tertiary) = result.tieAttrs
//
////        TODO()
//            /*val existingTeis = filterTiesByAttributes(
//            repository.getTieByType(taskTIETypeUid, orgUnitUid, taskProgramUid),
//            primaryAttributeUid,
//            primary // tieuid not primary
//        )
//
//        val openTies = existingTeis.filter { tie ->
//            val status = getTaskStatus(tie.uid())
//            status != "COMPLETED" && status != "CANCELLED"
//        }
//
//        return if (openTies.isNotEmpty()) {
//            openTies.map { tei ->
//                updateTeiAttributes(tei.uid(), primary, secondary, tertiary)
//                Task(
//                    name = result.taskingConfig.name,
//                    description = result.taskingConfig.description,
//                    programUid = result.programUid,
//                    programName = repository.getProgramName(result.programUid),
//                    teiUid = tei.uid(),
//                    teiPrimary = primary,
//                    teiSecondary = secondary,
//                    teiTertiary = tertiary,
//                    dueDate = result.dueDate,
//                    priority = result.taskingConfig.priority,
//                    status = "OPEN"
//                )
//            }
//        } else {*/
//            val newTeiUid = d2.trackedEntityModule().trackedEntityInstances()
//                .blockingAdd(
//                    TrackedEntityInstanceCreateProjection.builder()
//                        .organisationUnit(result.orgUnit)
//                        .trackedEntityType(taskTIETypeUid)
//                        .build()
//                )
//
//
//
//            try {
//                val enrollmentUid = d2.enrollmentModule().enrollments()
//                    .blockingAdd(
//                        EnrollmentCreateProjection.builder()
//                            .trackedEntityInstance(newTeiUid)
//                            .program(taskProgramUid)
//                            .organisationUnit(result.orgUnit)
//                            .build()
//                    )
//
//                val today = Date()
//
//                d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(today)
//                d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(today)
//                d2.enrollmentModule().enrollments().uid(enrollmentUid).setStatus(EnrollmentStatus.ACTIVE)
//
//            } catch (d2Error: D2Error) {
//                Timber.tag("EnrollmentCreation")
//                    .d("Failed: code=${d2Error.errorCode()} desc=${d2Error.errorDescription()}")
//            }
//
//            /*val enrollmentUid = d2.enrollmentModule().enrollments()
//                .blockingAdd(
//                    EnrollmentCreateProjection.builder()
//                        .trackedEntityInstance(newTeiUid)
//                        .program(taskProgramUid)
//                        .organisationUnit(result.orgUnit)
//                        .build()
//                )
//
//            d2.enrollmentModule().enrollments()
//                .uid(enrollmentUid)
//                .setEnrollmentDate(Date())
//            //.blockingUpdate()
//
//            d2.enrollmentModule().enrollments()
//                .uid(enrollmentUid)
//                .setIncidentDate(Date())
//            //.blockingUpdate()
//
//            d2.enrollmentModule().enrollments()
//                .uid(enrollmentUid)
//                .setStatus(EnrollmentStatus.ACTIVE)*/
//
//            // updateTeiAttributes(newTeiUid, primary, secondary, tertiary)
//
//
//            tasks.add(
//                Task(
//                    name = result.taskingConfig.name,
//                    description = result.taskingConfig.description,
//                    programUid = result.programUid,
//                    programName = repository.getProgramName(result.programUid),
//                    teiUid = newTeiUid,
//                    teiPrimary = primary,
//                    teiSecondary = secondary,
//                    teiTertiary = tertiary,
//                    dueDate = result.dueDate,
//                    priority = result.taskingConfig.priority,
//                    status = "OPEN"
//                )
//            )
//        }
//
//        Timber.tag("CREATED_TASKS").d(tasks.toString())
//
//        return  tasks.toList()
//    }
}

   /* fun filterTiesByAttributes(
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
        if (repository.statusAttributeUid.isEmpty()) return ""
        return d2.trackedEntityModule().trackedEntityAttributeValues()
            .byTrackedEntityInstance().eq(tieUid)
            .byTrackedEntityAttribute().eq(repository.statusAttributeUid)
            .one().blockingGet()?.value() ?: ""
    }

    private fun updateTeiAttributes(
        teiUid: String,
        primary: String,
        secondary: String?,
        tertiary: String?
    ) {
        d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(primaryAttributeUid, teiUid)
            .blockingSet(primary)

        secondary?.let {
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(secondaryAttributeUid, teiUid)
                .blockingSet(it)
        }

        tertiary?.let {
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(tertiaryAttributeUid, teiUid)
                .blockingSet(it)
        }
    }
}
*/