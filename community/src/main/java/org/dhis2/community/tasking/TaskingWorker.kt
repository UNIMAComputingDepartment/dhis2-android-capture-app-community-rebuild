package org.dhis2.community.tasking

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.dhis2.community.tasking.engine.DefaultingEvaluator
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2

class TaskingWorker(
    context: Context, workerParameters: WorkerParameters, private val d2: D2
) : Worker(context, workerParameters) {

    override fun doWork(): Result {
        defaultInvalidTasks()
        return Result.success()
    }

    private fun defaultInvalidTasks() {
        val repo = TaskingRepository(d2)

        DefaultingEvaluator(repo)
            .evaluateForDefaultingEnrollment(
                repo.getAllTasks().map {
                    it.sourceEnrollmentUid
                }
            )
    }
}