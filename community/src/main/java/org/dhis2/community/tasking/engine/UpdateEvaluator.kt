package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import timber.log.Timber
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UpdateEvaluator(
    private val repository: TaskingRepository
): TaskingEvaluator(repository) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateForUpdate(
        sourceTeiUid: String,
        programUid: String
    ) {
        val tasks = repository.getTasksForTei(sourceTeiUid)
        val programTaskConfig = repository.getTaskingConfig().programTasks
            .firstOrNull { it.programUid == programUid }
            ?: return

        tasks.forEach { existingTask ->
            val secondaryAttrUid =
                repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskSecondaryAttrUid
                    ?: ""
            val tertiaryAttrUid =
                repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskTertiaryAttrUid
                    ?: ""
            val dueDateAttrUid =
                repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.dueDateUid ?: ""
            val primaryAttrUid =
                repository.getCachedConfig()?.taskProgramConfig?.firstOrNull()?.taskPrimaryAttrUid
                    ?: ""

            val (primary, secondary, tertiary) = super.getTeiAttributes(sourceTeiUid, programTaskConfig.teiView)

            repository.updateTaskAttrValue(tertiaryAttrUid, tertiary, existingTask.teiUid)
            repository.updateTaskAttrValue(secondaryAttrUid, secondary, existingTask.teiUid)
            repository.updateTaskAttrValue(primaryAttrUid, primary, existingTask.teiUid)

            val taskConfig = programTaskConfig.taskConfigs
                .firstOrNull { it.name == existingTask.name }
                ?: return@forEach

            if (!taskConfig.period.anchor.uid.isNullOrBlank()) {
                val newDueDate = super.calculateDueDate(taskConfig, sourceTeiUid)
                repository.updateTaskAttrValue(dueDateAttrUid, newDueDate, existingTask.teiUid)
            }
            return@forEach
        }

    }
}

private fun TaskingRepository.getTasksForTei(teiUid: String): List<Task> {
    return this.getAllTasks().filter { it.sourceTeiUid == teiUid }
}
