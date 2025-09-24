package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber

class DefaultingEvaluator(
    private val repository: TaskingRepository,
    private val d2: D2,
) : TaskingEvaluator(d2, repository) {

    /**
     * Default a task if its trigger condition is no longer met but the task still exists.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun defaultTasks(
        targetProgramUid: String,
        sourceTieUid: String?,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String
    ): List<Task> {
        val allAvailableTasks = repository.getAllTasks()
        val tasksToDefault = mutableListOf<Task>()

        // Only consider tasks that are open and not completed/defaulted
        val openTasks = allAvailableTasks.filter { task ->
            task.sourceProgramUid == targetProgramUid &&
            task.status.lowercase() == "open" &&
            task.sourceEnrollmentUid == sourceTieProgramEnrollment
        }

        val config = repository.getTaskingConfig()
        val configsForProgram = config.programTasks.firstOrNull { it.programUid == targetProgramUid } ?: return emptyList()

        openTasks.forEach { task ->
            val taskConfig = configsForProgram.taskConfigs.firstOrNull { it.name == task.name } ?: return@forEach



            // Evaluate trigger using TaskingEvaluator logic
            val triggerResults = evaluateTriggerConditions(taskConfig, sourceTieUid ?: task.teiUid, targetProgramUid)
            val isTriggered = triggerResults.any { it.isTriggered }
            if (!isTriggered) {
                // Task exists and trigger is not met, so default it
                repository.updateTaskAttrValue(
                    repository.taskStatusAttributeUid,
                    "defaulted",
                    task.teiUid
                )
                tasksToDefault.add(task)
            }
        }
        Timber.tag("DEFAULTED_TASKS").d(tasksToDefault.toString())
        return tasksToDefault
    }
}
