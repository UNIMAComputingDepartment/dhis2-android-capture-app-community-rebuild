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
    fun createTasks(taskProgramUid: String,
                    taskTIETypeUid: String,
                    targetProgramUid: String,
                    sourceTieUid: String?,
                    sourceTieOrgUnitUid: String
                    /*result: EvaluationResult*/
    ): List<Task> {

        val evaluatedResult = evaluateForTie(
            sourceTieUid,
            targetProgramUid,
            sourceTieOrgUnitUid,
        )

        val tasks = mutableListOf<Task>()
        evaluatedResult.forEach { result ->
            if (result.tieAttrs == null || result.dueDate == null) return emptyList()

        val (primary, secondary, tertiary) = result.tieAttrs

//        TODO()
            /*val existingTeis = filterTiesByAttributes(
            repository.getTieByType(taskTIETypeUid, orgUnitUid, taskProgramUid),
            primaryAttributeUid,
            primary // tieuid not primary
        )

        val openTies = existingTeis.filter { tie ->
            val status = getTaskStatus(tie.uid())
            status != "COMPLETED" && status != "CANCELLED"
        }

        return if (openTies.isNotEmpty()) {
            openTies.map { tei ->
                updateTeiAttributes(tei.uid(), primary, secondary, tertiary)
                Task(
                    name = result.taskingConfig.name,
                    description = result.taskingConfig.description,
                    programUid = result.programUid,
                    programName = repository.getProgramName(result.programUid),
                    teiUid = tei.uid(),
                    teiPrimary = primary,
                    teiSecondary = secondary,
                    teiTertiary = tertiary,
                    dueDate = result.dueDate,
                    priority = result.taskingConfig.priority,
                    status = "OPEN"
                )
            }
        } else {*/
            val newTeiUid = d2.trackedEntityModule().trackedEntityInstances()
                .blockingAdd(
                    TrackedEntityInstanceCreateProjection.builder()
                        .organisationUnit(result.orgUnit)
                        .trackedEntityType(taskTIETypeUid)
                        .build()
                )



            try {
                val enrollmentUid = d2.enrollmentModule().enrollments()
                    .blockingAdd(
                        EnrollmentCreateProjection.builder()
                            .trackedEntityInstance(newTeiUid)
                            .program(taskProgramUid)
                            .organisationUnit(result.orgUnit)
                            .build()
                    )

                val today = Date()

                d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(today)
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(today)
                d2.enrollmentModule().enrollments().uid(enrollmentUid).setStatus(EnrollmentStatus.ACTIVE)

            } catch (d2Error: D2Error) {
                Timber.tag("EnrollmentCreation")
                    .d("Failed: code=${d2Error.errorCode()} desc=${d2Error.errorDescription()}")
            }

            /*val enrollmentUid = d2.enrollmentModule().enrollments()
                .blockingAdd(
                    EnrollmentCreateProjection.builder()
                        .trackedEntityInstance(newTeiUid)
                        .program(taskProgramUid)
                        .organisationUnit(result.orgUnit)
                        .build()
                )

            d2.enrollmentModule().enrollments()
                .uid(enrollmentUid)
                .setEnrollmentDate(Date())
            //.blockingUpdate()

            d2.enrollmentModule().enrollments()
                .uid(enrollmentUid)
                .setIncidentDate(Date())
            //.blockingUpdate()

            d2.enrollmentModule().enrollments()
                .uid(enrollmentUid)
                .setStatus(EnrollmentStatus.ACTIVE)*/

            // updateTeiAttributes(newTeiUid, primary, secondary, tertiary)


            tasks.add(
                Task(
                    name = result.taskingConfig.name,
                    description = result.taskingConfig.description,
                    programUid = result.programUid,
                    programName = repository.getProgramName(result.programUid),
                    teiUid = newTeiUid,
                    teiPrimary = primary,
                    teiSecondary = secondary,
                    teiTertiary = tertiary,
                    dueDate = result.dueDate,
                    priority = result.taskingConfig.priority,
                    status = "OPEN"
                )
            )
        }

        Timber.tag("CREATED_TASKS").d(tasks.toString())

        return  tasks.toList()
    }
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