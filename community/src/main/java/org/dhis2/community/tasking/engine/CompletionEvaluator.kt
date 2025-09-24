package org.dhis2.community.tasking.engine

import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import timber.log.Timber
import java.util.Date

class CompletionEvaluator(
    private val repository: TaskingRepository
) : TaskingEvaluator(repository) {

    fun taskCompletion(
        tasks: List<Task>,
        sourceProgramEnrollmentUid: String,
        sourceProgramUid: String,
        sourceTeiUid: String?
    ) {
        val taskConf = repository.getTaskingConfig()
        require(taskConf.programTasks.isNotEmpty()) { "Task Config is Empty" }

        val configForPg = taskConf.programTasks
            .filter { it.programUid == sourceProgramUid }
            .flatMap { it.taskConfigs }

        if (configForPg.isEmpty()) return

        val taskProgramUid = taskConf.taskProgramConfig.firstOrNull()?.programUid


        tasks.filter{it.sourceProgramUid == sourceProgramUid}
            .forEach { task ->

                val taskConfig = configForPg.firstOrNull() { it.name == task.name }
                if (taskConfig == null){
                    return@forEach
                }

                if (task.sourceEnrollmentUid == sourceProgramEnrollmentUid) {
                    val conditions = evaluateConditions(
                        conditions = taskConfig.completion,
                        teiUid = sourceTeiUid!!,
                        programUid = sourceProgramUid
                    )

                    if(conditions.any{it}) {
                        repository.updateTaskAttrValue(
                            repository.taskStatusAttributeUid,
                            "completed",
                            task.teiUid
                        )

                        val taskTeiEnrollmentUid = repository.d2.enrollmentModule().enrollments()
                            .byTrackedEntityInstance().eq(task.teiUid)
                            .byProgram().eq(taskProgramUid)
                            .byStatus().eq(EnrollmentStatus.ACTIVE)
                            .one().blockingGet()?.uid()

                        if (taskTeiEnrollmentUid != null) {
                            repository.d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid)
                                .setStatus(EnrollmentStatus.COMPLETED)
                            repository.d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid)
                                .setCompletedDate(Date())
                        }   else{
                            Timber.d("No active enrollment")
                        }
                    }

                } else null
            }
    }
}
