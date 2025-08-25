package org.dhis2.community.tasking.engine

//import kotlinx.datetime.LocalDate
import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository

open class TaskingEvaluator(
    private val repository: TaskingRepository
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateForTie(
        tieUid: String,
        programUid: String
    ): List<EvaluationResult> {

        val config: TaskingConfig = repository.getTaskingConfig()
        val result = mutableListOf<EvaluationResult>()

        config.taskConfigs.forEach { taskConfig ->
            if (taskConfig.trigger.program == programUid) {
                val shouldTrigger =
                    repository.evaluateCondition(taskConfig.trigger.condition, tieUid)

                if (shouldTrigger) {
                    val dueDate = repository.calculateDueDate(taskConfig, tieUid, programUid)
                    val tieAttrs = repository.getTieAttributes(tieUid, taskConfig.teiView)

                    result.add(
                        EvaluationResult(
                            taskingConfig = taskConfig,
                            teiUid = tieUid,
                            programUid = programUid,
                            isTriggered = true,
                            dueDate = dueDate,
                            tieAttrs = tieAttrs
                        )
                    )
                } else {
                    result.add(
                        EvaluationResult(
                            taskingConfig = taskConfig,
                            teiUid = tieUid,
                            programUid = programUid,
                            isTriggered = false
                        )
                    )
                }
            }
        }
        return result
    }
}