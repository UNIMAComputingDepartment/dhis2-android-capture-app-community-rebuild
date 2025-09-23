package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import timber.log.Timber


class CreationEvaluator (
    private val repository: TaskingRepository
) : TaskingEvaluator(repository){

    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateForCreation(
        taskProgramUid: String,
        taskTEITypeUid: String,
        targetProgramUid: String,
        sourceTeiUid: String?,
        sourceTeiOrgUnitUid: String,
        sourceTeiProgramEnrollment: String
    ) {
        if (sourceTeiUid == null) {
            Timber.e("CreationEvaluator: sourceTeiUid is null")
            return
        }

        val config = repository.getTaskingConfig()
        val configsForProgram = config.programTasks.firstOrNull() { it.programUid == targetProgramUid }
        if (configsForProgram == null) {
            Timber.e("CreationEvaluator: No tasking config found for program $targetProgramUid")
            return
        }

        configsForProgram.taskConfigs.forEach { taskConfig ->
            val isTriggered = evaluateConditions(
                conditions = taskConfig.trigger,
                teiUid = sourceTeiUid,
                targetProgramUid).any { it }

            if (isTriggered && notDuplicateTask(taskConfig, targetProgramUid, sourceTeiProgramEnrollment)
                ) {
                val res = createTaskForTei(
                    taskConfig,
                    configsForProgram.teiView,
                    taskProgramUid,
                    taskTEITypeUid,
                    targetProgramUid,
                    sourceTeiUid,
                    sourceTeiOrgUnitUid,
                    sourceTeiProgramEnrollment
                )
                Timber.d("Task ${taskConfig.name} creation result: $res")
            }
        }
    }

    private fun notDuplicateTask(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        targetProgramUid: String,
        sourceTeiProgramEnrollment: String,
    ): Boolean {
        val allAvailableTasks = repository.getAllTasks()
        val taskAlreadyExist = allAvailableTasks.any { task ->
            task.sourceProgramUid == targetProgramUid &&
                    task.status != "completed" &&
                    task.sourceEnrollmentUid == sourceTeiProgramEnrollment &&
                    task.name == taskConfig.name
        }
        Timber.d("Task ${taskConfig.name} already exists: $taskAlreadyExist")
        return !taskAlreadyExist
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createTaskForTei(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiView: TaskingConfig.ProgramTasks.TeiView,
        taskProgramUid: String,
        taskTEITypeUid: String,
        targetProgramUid: String,
        sourceTeiUid: String,
        sourceTeiOrgUnitUid: String,
        sourceTeiProgramEnrollment: String
    ): Boolean {

        val (primary, secondary, tertiary) = getTeiAttributes(sourceTeiUid, teiView)

        val task = Task(
            name = taskConfig.name,
            description = taskConfig.description,
            sourceProgramUid = targetProgramUid,
            sourceProgramName = repository.getProgramName(targetProgramUid),
            teiUid = "", // Will be set after creation
            teiPrimary = primary,
            teiSecondary = secondary,
            teiTertiary = tertiary,
            dueDate = calculateDueDate(taskConfig, sourceTeiUid).toString(),
            priority = taskConfig.priority,
            status = "OPEN",
            sourceEnrollmentUid = sourceTeiProgramEnrollment,
            sourceTeiUid = sourceTeiUid,
            iconNane = repository.getSourceProgramIcon(targetProgramUid)
        )

        return repository.createTask(
            task,
            sourceTeiOrgUnitUid,
            taskTEITypeUid,
            taskProgramUid,
        )
    }
}

