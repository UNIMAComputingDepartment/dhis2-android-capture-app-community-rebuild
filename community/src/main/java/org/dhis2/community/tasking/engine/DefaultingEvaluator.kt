package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

class DefaultingEvaluator(
    private val repository: TaskingRepository,
    private val d2: D2,
) : TaskingEvaluator(d2, repository) {

    /**
     * Evaluates and sets tasks as "defaulted" if:
     *  - The task is "open" and not "completed"
     *  - The trigger condition is no longer met (see evaluateDefaultingConditions)
     *  - The task still exists for the enrollment
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

        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        openTasks.forEach { task ->
            val taskConfig = configsForProgram.taskConfigs.firstOrNull { it.name == task.name } ?: return@forEach
            val defaultingResults = evaluateDefaultingConditions(
                taskConfig = taskConfig,
                teiUid = sourceTieUid ?: task.teiUid,
                programUid = targetProgramUid,
                enrollmentUid = sourceTieProgramEnrollment
            )
            // Default if trigger is no longer met
            if (defaultingResults.any { it.isTriggered }) {
                repository.updateTaskAttrValue(
                    repository.taskStatusAttributeUid,
                    "defaulted",
                    task.teiUid
                )
                tasksToDefault += task.copy(status = "DEFAULTED")
                return@forEach
            }
            // Default if overdue by 2+ days
            val dueDateStr = task.dueDate
            if (dueDateStr.matches(Regex("\\d{2}-\\d{2}-\\d{4}"))) {
                val dueDate = LocalDate.parse(dueDateStr, formatter)
                if (dueDate.plusDays(2).isBefore(today)) {
                    repository.updateTaskAttrValue(
                        repository.taskStatusAttributeUid,
                        "defaulted",
                        task.teiUid
                    )
                    tasksToDefault += task.copy(status = "DEFAULTED")
                }
            }
        }
        Timber.tag("DefaultingEvaluator").d("Defaulted tasks: $tasksToDefault")
        return tasksToDefault
    }
}
