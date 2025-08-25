package org.dhis2.community.tasking.engine

import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.event.EventStatus

class CompletionEvaluator(
    private val d2: D2,
    private val repository: TaskingRepository
) : TaskingEvaluator(repository) {

    fun completeTaskIfFollowupDone(
        taskTeiUid: String,
        programUid: String,
        stageUid: String,
        completionDataElement: String? = null,  // optional DE
        completionValue: String? = null         // optional expected value
    ): Boolean {

        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(taskTeiUid)
            .byProgram().eq(programUid)
            .blockingGet()

        if (enrollments.isEmpty()) return false

        val followupEvents = enrollments.flatMap { enrollment ->
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollment.uid())
                .byProgramStageUid().eq(stageUid)
                .withTrackedEntityDataValues()
                .blockingGet()
        }

        val completed = if (completionDataElement != null && completionValue != null) {
            followupEvents.any { event ->
                event.trackedEntityDataValues()
                    ?.any { it.dataElement() == completionDataElement && it.value() == completionValue } == true
            }
        } else {
            followupEvents.any { it.status() == EventStatus.COMPLETED }
        }

        if (completed) {
            repository.updateTaskStatus(taskTeiUid, "COMPLETED")

            enrollments.forEach { enrollment ->
                d2.enrollmentModule().enrollments()
                    .uid(enrollment.uid())
                    .setStatus(EnrollmentStatus.COMPLETED)
                    //.blockingUpdate()
            }
            return true
        }

        return false
    }
}
