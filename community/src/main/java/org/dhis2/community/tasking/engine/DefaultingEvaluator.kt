package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber

class DefaultingEvaluator(
    private val repository: TaskingRepository,
    private val d2: D2,
) : TaskingEvaluator(d2, repository) {

    // Constants for the visit type attribute and option codes
    private companion object {
        const val VISIT_TYPE_ATTRIBUTE_UID = "YOUR_VISIT_TYPE_ATTRIBUTE_UID" // Replace with actual UID
        const val SCHEDULED_VISIT_CODE = "1"
        const val FOLLOW_UP_VISIT_CODE = "2"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun defaultTasks(
        targetProgramUid: String,
        sourceTieUid: String?,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String
    ): List<Task> {
        val allAvailableTasks = repository.getAllTasks()
        val tasksToDefault = mutableListOf<Task>()

        val openTasks = allAvailableTasks.filter { task ->
            task.sourceProgramUid == targetProgramUid &&
                    task.status.lowercase() == "open" &&
                    task.sourceEnrollmentUid == sourceTieProgramEnrollment
        }

        val config = repository.getTaskingConfig()
        val configsForProgram = config.programTasks.firstOrNull { it.programUid == targetProgramUid } ?: return emptyList()

        openTasks.forEach { task ->
            val taskConfig = configsForProgram.taskConfigs.firstOrNull { it.name == task.name } ?: return@forEach

            // Evaluate the trigger condition for this task
            val triggerResults = evaluateTriggerConditions(taskConfig, sourceTieUid ?: task.teiUid, targetProgramUid)
            val isTriggered = triggerResults.any { it.isTriggered }

            if (!isTriggered) {
                Timber.tag("DEFAULTING").d("Defaulting task ${task.name} due to trigger condition no longer met")
                repository.updateTaskAttrValue(repository.taskStatusAttributeUid, "defaulted", task.teiUid)
                tasksToDefault.add(task)
            }
        }

        Timber.tag("DEFAULTED_TASKS").d(tasksToDefault.toString())
        return tasksToDefault
    }

    private fun isFollowUpTaskByName(taskConfig: TaskingConfig.ProgramTasks.TaskConfig): Boolean {
        // Check task name and description for Follow-up indicators
        return taskConfig.name.contains("follow up", ignoreCase = true) ||
                taskConfig.name.contains("follow-up", ignoreCase = true) ||
                taskConfig.name.contains("followup", ignoreCase = true) ||
                taskConfig.description.contains("follow up", ignoreCase = true) ||
                taskConfig.description.contains("follow-up", ignoreCase = true) ||
                taskConfig.description.contains("followup", ignoreCase = true)
    }

    private fun getCurrentVisitType(enrollmentUid: String): String {
        return try {
            // Get the TEI from enrollment
            val enrollment = d2.enrollmentModule().enrollments().uid(enrollmentUid).blockingGet()
            val teiUid = enrollment?.trackedEntityInstance() ?: return ""

            // Query the visit type attribute value
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(teiUid)
                .byTrackedEntityAttribute().eq(VISIT_TYPE_ATTRIBUTE_UID)
                .one().blockingGet()?.value() ?: ""
        } catch (e: Exception) {
            Timber.e("Error getting visit type: ${e.message}")
            ""
        }
    }

    private fun wasTaskTriggeredByFollowUp(task: Task, enrollmentUid: String): Boolean {
        // Since we can't store historical data in config, use a practical approach:
        // If the task exists and is currently associated with this enrollment,
        // check if the current enrollment was ever Follow-up type

        // For now, we'll check the current state and assume it reflects the original state
        // In a more robust implementation, you might check task creation timestamp
        val currentVisitType = getCurrentVisitType(enrollmentUid)
        return currentVisitType == FOLLOW_UP_VISIT_CODE
    }

    // Method for handling deleted enrollments (for your next requirement)
    @RequiresApi(Build.VERSION_CODES.O)
    fun defaultTasksForDeletedEnrollment(enrollmentUid: String): List<Task> {
        val allAvailableTasks = repository.getAllTasks()
        val tasksToDefault = mutableListOf<Task>()

        val tasksForEnrollment = allAvailableTasks.filter { task ->
            task.sourceEnrollmentUid == enrollmentUid && task.status.lowercase() == "open"
        }

        tasksForEnrollment.forEach { task ->
            Timber.tag("DEFAULTING").d("Defaulting task ${task.name} due to deleted enrollment: $enrollmentUid")
            repository.updateTaskAttrValue(repository.taskStatusAttributeUid, "defaulted", task.teiUid)
            tasksToDefault.add(task)
        }

        Timber.tag("DELETED_DEFAULTED").d("Enrollment: $enrollmentUid, Tasks: ${tasksToDefault.size}")
        return tasksToDefault
    }
}