package org.dhis2.community.tasking.engine

//import kotlinx.datetime.LocalDate
import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.Enrollment

open class TaskingEvaluator(
    private val d2: D2,
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
                    evaluateCondition(taskConfig.trigger.condition, tieUid)

                if (shouldTrigger) {
                    val dueDate = repository.calculateDueDate(taskConfig, tieUid, programUid)
                        ?: return@forEach
                    val tieAttrs = getTieAttributes(tieUid, taskConfig.teiView)

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


    fun getTieAttributes(
        tieUid: String,
        tieView: TaskingConfig.TaskConfig.TeiView
    ): Triple<String, String, String> {
        fun getAttr(uid: String) =
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tieUid)
                .byTrackedEntityAttribute().eq(uid)
                .one().blockingGet()?.value() ?: ""

        return Triple(
            getAttr(tieView.teiPrimaryAttribute),
            getAttr(tieView.teiSecondaryAttribute),
            getAttr(tieView.teiTertiaryAttribute)
        )
    }

    private fun evaluateCondition(
        condition: TaskingConfig.TaskConfig.Condition,
        teiUid: String
    ): Boolean {
        val lhsValue = resolvedReference(condition.lhs, teiUid)
        val rhsValue = condition.rhs.value

        return when (condition.op) {
            "EQUALS" -> lhsValue == rhsValue
            "NOT_EQUALS" -> lhsValue != rhsValue
            else -> false
        }
    }

    private fun resolvedReference(
        ref: TaskingConfig.TaskConfig.Reference,
        teiUid: String,
        programUid: String? = null
    ): Any? {
        return when (ref.ref) {
            "teiAttribute" -> {
                ref.uid?.let { uid ->
                    d2.trackedEntityModule().trackedEntityAttributeValues()
                        .byTrackedEntityInstance().eq(teiUid)
                        .byTrackedEntityAttribute().eq(uid)
                        .one().blockingGet()?.value()
                }
            }

            "eventData" -> {
                if (programUid == null || ref.uid == null) return null

                val enrollment = repository.getLatestEnrollment(teiUid, programUid) ?: return null
                val events = d2.eventModule().events()
                    .byEnrollmentUid().eq(enrollment.uid())
                    .byProgramStageUid().eq(ref.uid)
                    .withTrackedEntityDataValues()
                    .blockingGet()

                events.firstOrNull()?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == ref.uid }
                    ?.value()
            }

            "static" -> ref.value?.toString()
            else -> null
        }
    }
}