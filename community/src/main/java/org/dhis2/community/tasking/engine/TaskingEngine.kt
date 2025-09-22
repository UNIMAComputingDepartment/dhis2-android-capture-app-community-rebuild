package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.repositories.TaskingRepository
import timber.log.Timber

class TaskingEngine(
    private val repository: TaskingRepository,
) {
    private val TAG = TaskingEngine::class.java.simpleName
    private val creationEvaluator = CreationEvaluator(repository)
    private val completionEvaluator = CompletionEvaluator(repository)
    private val updateEvaluator = UpdateEvaluator(repository)

    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluate(
        targetProgramUid: String,
        sourceTieUid: String,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String,
        isEventTrigger: Boolean = true
    ) {
        val taskProgramUid = repository.getTaskingConfig().taskProgramConfig.first().programUid
        val taskTIETypeUid = repository.getTaskingConfig().taskProgramConfig.first().teiTypeUid

        if (!isEventTrigger) {
            updateEvaluator.evaluateForUpdate(sourceTieUid, targetProgramUid)
        }

        completionEvaluator.taskCompletion(
            tasks = repository.getTasksPerOrgUnit(sourceTieOrgUnitUid),
            sourceProgramEnrollmentUid = sourceTieProgramEnrollment,
            sourceProgramUid = targetProgramUid,
            sourceTeiUid = sourceTieUid
        )

        val createdTasks = creationEvaluator.evaluateForCreation(
            taskProgramUid,
            taskTIETypeUid,
            targetProgramUid,
            sourceTieUid,
            sourceTieOrgUnitUid,
            sourceTieProgramEnrollment
        )

        Timber.tag(TAG).d("Created tasks: $createdTasks")

    }

}