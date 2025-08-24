package org.dhis2.community.tasking.engine

import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import java.util.Date

class CreationEvaluator(
    private val repository: TaskingRepository,
    private val d2: D2,
    private val orgUnitUid: String,
    private val taskProgramUid: String,
    private val taskTIETypeUid: String,
    private val primaryAttributeUid: String,
    private val secondaryAttributeUid: String,
    private val tertiaryAttributeUid: String
) : TaskingEvaluator(repository) {

    fun createTasks(result: EvaluationResult): List<Task> {
        if (!result.isTriggered || result.tieAttrs == null || result.dueDate == null) return emptyList()

        val (primary, secondary, tertiary) = result.tieAttrs

        val existingTeis = repository.filterTiesByAttributes(
            repository.getTieByType(taskTIETypeUid, orgUnitUid, taskProgramUid),
            primaryAttributeUid,
            primary
        )

        return if (existingTeis.isNotEmpty()) {
            existingTeis.map { tei ->
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
        } else {
            val newTeiUid = d2.trackedEntityModule().trackedEntityInstances()
                .blockingAdd(
                    TrackedEntityInstanceCreateProjection.builder()
                        .organisationUnit(orgUnitUid)
                        .trackedEntityType(taskTIETypeUid)
                        .build()
                )

            val enrollmentUid = d2.enrollmentModule().enrollments()
                .blockingAdd(
                    EnrollmentCreateProjection.builder()
                        .trackedEntityInstance(newTeiUid)
                        .program(taskProgramUid)
                        .organisationUnit(orgUnitUid)
                        .build()
                )

            d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(Date())
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(Date())

            updateTeiAttributes(newTeiUid, primary, secondary, tertiary)

            listOf(
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
