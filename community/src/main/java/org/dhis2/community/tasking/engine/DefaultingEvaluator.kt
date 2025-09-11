package org.dhis2.community.tasking.engine

import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.event.EventStatus

class DefaultingEvaluator(
    private val d2: D2,
    private val repository: TaskingRepository
) : TaskingEvaluator(d2, repository) {

    /*
    fun defaultTaskIfSourceValueChanged(
        taskTeiUid: String,
        sourceProgramUid: String,
        sourceStageUid: String,
        sourceDataElement: String? = null,
        sourceValue: String? = null
    ): Boolean {

        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(taskTeiUid)
            .byProgram().eq(sourceProgramUid)
            .blockingGet()

        if (enrollments.isEmpty()) return true

        val sourceEvents = enrollments.flatMap { enrollment ->
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollment.uid())
                .byProgramStageUid().eq(sourceStageUid)
                .withTrackedEntityDataValues()
                .blockingGet()
        }

        if (sourceEvents.isEmpty()) return true

        val shouldDefault = if (sourceDataElement != null && sourceValue != null) {
            sourceEvents.none { event ->
                event.trackedEntityDataValues()
                    ?.any {it.dataElement() == sourceDataElement && it.value() == sourceValue} == true
            }
        } else {
            sourceEvents.all { it.status() == EventStatus.SKIPPED}
        }

            if (shouldDefault) {
                repository.updateTaskStatus(taskTeiUid, "DEFAULTED")

                enrollments.forEach { enrollment ->
                    d2.enrollmentModule().enrollments()
                        .uid(enrollment.uid())
                        .setStatus(EnrollmentStatus.CANCELLED)
                }
                return true
            }
            return false
        }
    */

    fun defaultTaskIfTriggerChanged(
        taskTeiUid: String,
        programUid: String,
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig
    ): Boolean {
        val triggerActive = (TaskingEvaluator(d2, repository)).isTriggerActive(taskConfig, taskTeiUid, programUid)
        if (!triggerActive) {
            repository.updateTaskAttrValue(repository.taskStatusAttributeUid, "DEFAULTED", taskTeiUid)
            val enrollments = d2.enrollmentModule().enrollments()
                .byTrackedEntityInstance().eq(taskTeiUid)
                .byProgram().eq(programUid)
                .blockingGet()
            enrollments.forEach { enrollment ->
                d2.enrollmentModule().enrollments()
                    .uid(enrollment.uid())
                    .setStatus(EnrollmentStatus.CANCELLED)
            }
            return true
        }
        return false
    }
}