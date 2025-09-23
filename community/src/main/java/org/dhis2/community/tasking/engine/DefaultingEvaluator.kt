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
        taskProgramUid: String
    ){
        val tasks = repository.getTasksForTei(sourceTeiUid)

        val programTaskConfig = repository.getTaskingConfig().programTasks
            .firstOrNull { it.programUid == programUid }
            ?: return

        tasks.forEach { task ->
            val taskTriggerEventUid = "task.triggerEvent" ?: return
            val taskConfigs = programTaskConfig.taskConfigs.first { it.name == task.name }

            val taskTeiEnrollmentUid = repository.d2.enrollmentModule().enrollments()
                .byTrackedEntityInstance().eq(task.teiUid)
                .byProgram().eq(taskProgramUid)
                .byStatus().eq(EnrollmentStatus.ACTIVE)
                .one().blockingGet()?.uid()


            if (evaluateForDefaultingConditions(
                configs = taskConfigs,
                teiUid = task.teiUid,
                programUid = programUid,
                taskTriggerEventUid
            )){
                repository.updateTaskAttrValue(
                    repository.taskStatusAttributeUid,
                    "defaulted",
                    task.teiUid)

                repository.d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid).setStatus(EnrollmentStatus.CANCELLED)
                repository.d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid).setCompletedDate(Date())
            }
        }

    }

    private fun TaskingRepository.getTasksForTei(teiUid: String): List<Task> {
        return this.getAllTasks().filter { it.sourceTeiUid == teiUid }
    }
}