package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber
import javax.inject.Inject

class TaskingOrchestrator(
    private val creationEvaluator: CreationEvaluator,
    private val defaultingEvaluator: DefaultingEvaluator,
    private val taskingEvaluator: TaskingEvaluator,
    private val repository: TaskingRepository,
    private val d2: D2
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateTasks(
        taskProgramUid: String,
        taskTIETypeUid: String,
        targetProgramUid: String,
        sourceTieUid: String?,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String
    ): Pair<List<Task>, List<Task>> {

        // 1. Defaulting first (checks visit type changes and trigger conditions)
        val defaultedTasks = defaultingEvaluator.defaultTasks(
            targetProgramUid, sourceTieUid, sourceTieOrgUnitUid, sourceTieProgramEnrollment
        )

        // 2. Get current tasks for completion check (after defaulting)
        val allTasksAfterDefaulting = repository.getAllTasks()
        val openTasks = allTasksAfterDefaulting.filter { it.status.equals("open", ignoreCase = true) }

        // 3. Completion check (only runs on non-defaulted tasks)
        taskingEvaluator.taskCompletion(
            tasks = openTasks,
            sourceProgramEnrollmentUid = sourceTieProgramEnrollment,
            sourceProgramUid = targetProgramUid,
            sourceTeiUid = sourceTieUid
        )

        // 4. Create new tasks for current conditions
        val newTasks = creationEvaluator.createTasks(
            taskProgramUid, taskTIETypeUid, targetProgramUid,
            sourceTieUid, sourceTieOrgUnitUid, sourceTieProgramEnrollment
        )

        return Pair(defaultedTasks, newTasks)
    }
}