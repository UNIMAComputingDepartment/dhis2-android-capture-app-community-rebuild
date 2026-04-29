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
        val config = repository.getTaskingConfig()

        try {
            val taskProgramConfig = repository.getTaskingConfig().taskProgramConfig.first()
            val taskProgramUid = taskProgramConfig.programUid
            val taskTIETypeUid = taskProgramConfig.teiTypeUid

            config.programTasks.forEach { programTask ->

                val targetProgramUid = programTask.programUid

                val tasksToRun = programTask.taskConfigs.filter { !it.frequency.isNullOrEmpty() }

                tasksToRun.forEach { tasksToRun ->
                    val teiUids =
                        repository.getTeiUidsWithActiveEnrollmentForProgram(programTask.programUid)


                    teiUids.forEach { teiUid ->
                        val sourceTeiOrgUnitUid = repository.getOrgUnit(teiUid)
                        val sourceTeiProgramEnrollmentUid = repository.getLatestEnrollment(teiUid, targetProgramUid)?.uid()
                        val eventUid = repository.getEnrollmentLatestEvent(sourceTeiProgramEnrollmentUid!!, targetProgramUid)?.uid()

                        if (eventUid.isNullOrBlank())
                            updateEvaluator.evaluateForUpdate(teiUid, targetProgramUid)

                        if (sourceTeiOrgUnitUid.isNullOrEmpty() || sourceTeiProgramEnrollmentUid.isEmpty())
                            return@forEach

                        defaultingEvaluator.periodicCheck()

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