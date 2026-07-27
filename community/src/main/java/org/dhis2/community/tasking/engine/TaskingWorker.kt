package org.dhis2.community.tasking.engine
import android.os.Build
import androidx.annotation.RequiresApi
import org.hisp.dhis.android.core.D2

import org.dhis2.community.tasking.repositories.TaskingRepository
import timber.log.Timber

class TaskingWorker(
    //private val d2: D2,
    private val repository: TaskingRepository,
    ){

    private val TAG = TaskingEngine::class.java.simpleName
    private val creationEvaluator = CreationEvaluator(repository)
    private val completionEvaluator = CompletionEvaluator(repository)
    private val updateEvaluator = UpdateEvaluator(repository)
    private val defaultingEvaluator = DefaultingEvaluator(repository)

    fun defaultingWorker(){
        DefaultingEvaluator(repository)
            .periodicCheck()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun periodicWorker(
    ){
        try {
            val config = repository.getTaskingConfig()
            val taskProgramConfig = repository.getTaskingConfig().taskProgramConfig.first()
            val taskProgramUid = taskProgramConfig.programUid
            val taskTIETypeUid = taskProgramConfig.teiTypeUid
            val taskingProgramUid = config.taskProgramConfig.first().programUid
            val hasTaskingProgramAccess = repository.hasDataAccessToTasking()

            if(!hasTaskingProgramAccess){
                Timber.tag(TAG).d("User does not have access to tasking program with uid: $taskingProgramUid. Skipping tasking evaluation.")
                return
            }

            config.programTasks.forEach { programTask ->

                val targetProgramUid = programTask.programUid

                val tasksToRun = programTask.taskConfigs.filter { !it.frequency.isNullOrEmpty() }

                tasksToRun.forEach { tasksToRun ->
                    val teiUids =
                        repository.getTeiUidsWithActiveEnrollmentForProgram(programTask.programUid)


                    teiUids.forEach { teiUid ->
                        val sourceTeiOrgUnitUid = repository.getOrgUnit(teiUid)
                        val sourceTeiProgramEnrollmentUid = repository.getLatestEnrollment(teiUid, targetProgramUid)?.uid()

                        // Guard before dereferencing: a TEI without an enrollment in the target
                        // program used to NPE on the getEnrollmentLatestEvent(...!!) below, and
                        // because the whole worker is wrapped in a rethrowing try/catch, that one
                        // TEI aborted the entire periodic sweep for every remaining TEI/program.
                        if (sourceTeiOrgUnitUid.isNullOrEmpty() || sourceTeiProgramEnrollmentUid.isNullOrEmpty())
                            return@forEach

                        val eventUid = repository.getEnrollmentLatestEvent(sourceTeiProgramEnrollmentUid, targetProgramUid)?.uid()

                        if (eventUid.isNullOrBlank())
                            updateEvaluator.evaluateForUpdate(teiUid, targetProgramUid)

                        // Defaulting is a global, argument-free scan of all tasks; it is already
                        // run once per sync via defaultingWorker(). Calling it here re-scanned every
                        // task for every (programTask x taskConfig x teiUid) combination.
                        completionEvaluator.taskCompletion(
                            tasks = repository.getTasks(),
                            sourceProgramEnrollmentUid = sourceTeiProgramEnrollmentUid,
                            sourceProgramUid = targetProgramUid,
                            sourceTeiUid = teiUid
                        )

                        val createdTasks = creationEvaluator.evaluateForCreation(
                            taskProgramUid = taskProgramUid,
                            taskTEITypeUid = taskTIETypeUid,
                            targetProgramUid = targetProgramUid,
                            sourceTeiUid = teiUid,
                            sourceTeiOrgUnitUid = sourceTeiOrgUnitUid,
                            sourceTeiProgramEnrollment = sourceTeiProgramEnrollmentUid
                        )

                        Timber.tag(TAG).d("Created Tasks: $createdTasks")
                    }
                }
            }
        } catch (throwable: Throwable){
            Timber.tag(TAG).d("TaskingWorker Evaluation failed")
            throw throwable
        }

    }
}