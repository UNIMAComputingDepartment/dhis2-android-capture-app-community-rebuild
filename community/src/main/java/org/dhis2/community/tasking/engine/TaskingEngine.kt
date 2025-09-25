package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.community.tasking.repositories.TaskingRepository
import timber.log.Timber

class TaskingEngine(
    private val repository: TaskingRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = TaskingEngine::class.java.simpleName
    private val creationEvaluator = CreationEvaluator(repository)
    private val completionEvaluator = CompletionEvaluator(repository)
    private val updateEvaluator = UpdateEvaluator(repository)
    private val defaultingEvaluator = DefaultingEvaluator(repository)

    // internal scope for fire-and-forget usage; cancel it when the owner is cleared
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Call this from a ViewModel/lifecycle owner to avoid leaks */
    fun clear() = (scope.coroutineContext[Job])?.cancel()

    /** Structured version: call from viewModelScope/lifecycleScope */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun evaluate(
        targetProgramUid: String,
        sourceTieUid: String,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String,
        eventUid: String? = null
    ) = withContext(ioDispatcher) {
        evaluateInternal(
            targetProgramUid,
            sourceTieUid,
            sourceTieOrgUnitUid,
            sourceTieProgramEnrollment,
            eventUid
        )
    }

    /** Fire-and-forget version that runs on a background thread and returns a Job you can cancel */
    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateAsync(
        targetProgramUid: String,
        sourceTieUid: String,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String,
        eventUid: String? = null
    ): Job = scope.launch {
        evaluateInternal(
            targetProgramUid,
            sourceTieUid,
            sourceTieOrgUnitUid,
            sourceTieProgramEnrollment,
            eventUid
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun evaluateInternal(
        targetProgramUid: String,
        sourceTieUid: String,
        sourceTieOrgUnitUid: String,
        sourceTieProgramEnrollment: String,
        eventUid: String?
    ) {
        try {
            val config = repository.getTaskingConfig().taskProgramConfig.first()
            val taskProgramUid = config.programUid
            val taskTIETypeUid = config.teiTypeUid

            if (!eventUid.isNullOrBlank()) {
                updateEvaluator.evaluateForUpdate(sourceTieUid, targetProgramUid)
            }

            eventUid?.let {
                defaultingEvaluator.evaluateForDefaultingEvent(
                    tasks = repository.getTasksPerOrgUnit(sourceTieUid),
                    sourceTeiUid = sourceTieUid,
                    programUid = targetProgramUid,
                    eventUid = it
                )
            }

            completionEvaluator.taskCompletion(
                tasks = repository.getTasks(),
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
                sourceTieProgramEnrollment,
                eventUid
            )

            Timber.tag(TAG).d("Created tasks: $createdTasks")
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "TaskingEngine.evaluate failed")
            throw t // rethrow if the caller needs to react
        }
    }
}