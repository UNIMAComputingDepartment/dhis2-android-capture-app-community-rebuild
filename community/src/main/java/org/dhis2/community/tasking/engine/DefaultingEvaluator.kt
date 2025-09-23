package org.dhis2.community.tasking.engine

import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import java.util.Date

class DefaultingEvaluator(
    private val repository: TaskingRepository
): TaskingEvaluator(repository){

    fun evaluateForDefaulting(
        sourceTeiUid: String,
        programUid: String,
        eventUid: String,
    ){
        val tasks = repository.getTasksForTei(sourceTeiUid)

        val programTaskConfig = repository.getTaskingConfig().programTasks
            .firstOrNull { it.programUid == programUid }
            ?: return

        tasks.forEach { task ->
            if (eventUid == task.sourceEventUid) {
                val taskTriggerEventUid = eventUid
                val taskConfigs = programTaskConfig.taskConfigs.first { it.name == task.name }

                val taskTeiEnrollmentUid = repository.d2.enrollmentModule().enrollments()
                    .byTrackedEntityInstance().eq(task.teiUid)
                    .byProgram().eq(repository.getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid)
                    .byStatus().eq(EnrollmentStatus.ACTIVE)
                    .one().blockingGet()?.uid()

                if (evaluateConditions(
                        conditions = taskConfigs.trigger,
                        teiUid = sourceTeiUid,
                        programUid = programUid,
                        eventUid = taskTriggerEventUid
                    ).all { it }){
                    repository.updateTaskAttrValue(
                        repository.taskStatusAttributeUid,
                        "defaulted",
                        task.teiUid)
                    repository.d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid).setStatus(EnrollmentStatus.CANCELLED)
                    repository.d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid).setCompletedDate(Date())
                }
            }
        }

    }

    private fun TaskingRepository.getTasksForTei(teiUid: String): List<Task> {
        return this.getAllTasks().filter { it.sourceTeiUid == teiUid && it.status != "completed" && it.status != "defaulted" }
    }
}