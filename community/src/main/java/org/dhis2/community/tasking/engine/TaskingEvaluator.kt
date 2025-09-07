

package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber

open class TaskingEvaluator(
    private val d2: D2,
    private val repository: TaskingRepository
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateForTie(
        sourceTieUid: String?,
        programUid: String,
        sourceTieOrgUnit: String
    ): List<EvaluationResult> {
        if (sourceTieUid == null) return emptyList()

        val config = repository.getTaskingConfig()
        require(config.taskConfigs.isNotEmpty()) { "Tasking Config is Empty" }

        val configsForProgram = config.taskConfigs.filter { it.trigger.programUid == programUid }
        if (configsForProgram.isEmpty()) return emptyList()

        val results = mutableListOf<EvaluationResult>()

        val ties = repository.getAllTrackedEntityInstances(programUid, sourceTieUid, sourceTieOrgUnit)
        ties.forEach { tei ->
            configsForProgram.forEach { taskConfig ->
                // Evaluate all conditions and return a list of results
                val evalResults = evaluateConditions(taskConfig, tei.uid(), programUid)
                evalResults.filter { it.isTriggered }.forEach { result ->
                    val dueDate = repository.calculateDueDate(taskConfig, tei.uid(), programUid)
                        ?: return@forEach
                    val tieAttrs = getTieAttributes(tei.uid(), taskConfig.teiView)
                    val taskTieOrgUnit = d2.enrollmentModule().enrollments()
                        .byTrackedEntityInstance().eq(tei.uid())
                        .blockingGet()
                        .firstOrNull()?.organisationUnit()

                    results += result.copy(
                        dueDate = dueDate,
                        tieAttrs = tieAttrs,
                        orgUnit = taskTieOrgUnit
                    )
                }
            }
        }

        Timber.tag("CREATED_TASK_EVALUATION").d(results.toString())
        return results
    }

    private fun evaluateConditions(
        taskConfig: TaskingConfig.TaskConfig,
        teiUid: String,
        programUid: String
    ): List<EvaluationResult> {
        return taskConfig.trigger.condition.map { cond ->
            val lhsValue =
                resolvedReference(taskConfig.trigger, teiUid, cond.lhs.uid.toString(), programUid)
            val rhsValue = cond.rhs.value

            val isTriggered = when (cond.op) {
                "EQUALS" -> lhsValue == rhsValue
                "NOT_EQUALS" -> lhsValue != rhsValue
                "NOT_NULL" -> !lhsValue.isNullOrEmpty()
                "NULL" -> lhsValue.isNullOrEmpty()
                else -> false
            }

            EvaluationResult(
                taskingConfig = taskConfig,
                teiUid = teiUid,
                programUid = programUid,
                isTriggered = isTriggered,
                dueDate = null, // to be filled later
                tieAttrs = Triple("", "", ""),
                orgUnit = null
            )
        }
    }

    private fun resolvedReference(
        trigger: TaskingConfig.TaskConfig.Trigger,
        teiUid: String,
        attrOrDataElementUid: String,
        programUid: String
    ): String? {
        return trigger.condition.firstNotNullOfOrNull { cond ->
            when (cond.lhs.ref) {
                "teiAttribute" -> d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byTrackedEntityAttribute().eq(attrOrDataElementUid)
                    .one().blockingGet()?.value()

                "eventData" -> {
                    val enrollment = repository.getLatestEnrollment(teiUid, programUid)
                        ?: return@firstNotNullOfOrNull null

                    val events = d2.eventModule().events()
                        .byEnrollmentUid().eq(enrollment.uid())
                        .withTrackedEntityDataValues()
                        .blockingGet()

                    events.asSequence()
                        .flatMap { it.trackedEntityDataValues() ?: emptyList() }
                        .firstOrNull { it.dataElement() == attrOrDataElementUid }
                        ?.value()
                }

                "static" -> cond.lhs.uid
                else -> null
            }
        }
    }

    private fun getTieAttributes(
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
}