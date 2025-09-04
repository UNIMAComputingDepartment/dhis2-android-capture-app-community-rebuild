package org.dhis2.community.tasking.engine

//import kotlinx.datetime.LocalDate
import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber

open class TaskingEvaluator (
    private val d2: D2,
    private val repository: TaskingRepository
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun evaluateForTie(
        sourceTieUid: String?,
        programUid: String,
        sourceTieOrgUnit: String
    ): List<EvaluationResult> {

        val config: TaskingConfig = repository.getTaskingConfig()
        //Timber.d("CREATE_TASK configurations=${config.taskConfigs}")

        config.taskConfigs.forEach { taskConfig ->
            Timber.d("TaskConfig: name=${taskConfig.name}, trigger=${taskConfig.trigger}, completion=${taskConfig.completion}")
        }
        require(config.taskConfigs.isNotEmpty()) {"Tasking Config is Empty"}
        val result = mutableListOf<EvaluationResult>()

        val ties = repository.getAllTrackedEntityInstances(programUid, sourceTieUid, sourceTieOrgUnit)
        Timber.d("CREATED_TASK_EVALUATION_TIES_FOUND: ${ties.size} for sourceTieUid=$sourceTieUid")


        ties.forEach { teiUid ->
            Timber.d("CREATED_TASK TEI UID: ${teiUid.uid()}")
            config.taskConfigs.forEach { taskConfig ->
                Timber.d("CREATED_TASK_EVALUATION TaskConfig trigger.program=${taskConfig.trigger.programName} for programUid=$programUid")

                if (/*taskConfig.trigger.program == programUid*/ true) {
                    val shouldTrigger =
                        evaluateCondition(taskConfig.trigger, sourceTieUid!!, programUid)

                    if (shouldTrigger) {
                        val dueDate =
                            repository.calculateDueDate(taskConfig, teiUid.uid(), programUid)
                                ?:
                                return@forEach
                        val tieAttrs = getTieAttributes(teiUid.uid(), taskConfig.teiView)

                        val taskTieOrgUnit = d2.enrollmentModule().enrollments()
                            .byTrackedEntityInstance().eq(teiUid.uid())
                            .blockingGet().firstOrNull()?.organisationUnit()

                        result.add(
                            EvaluationResult(
                                taskingConfig = taskConfig,
                                teiUid = teiUid.uid(),
                                programUid = programUid,
                                isTriggered = true,
                                dueDate = dueDate,
                                tieAttrs = tieAttrs,
                                orgUnit = taskTieOrgUnit
                            )
                        )
                    }
                }
            }
        }
        Timber.tag("CREATED_TASK_EVALUATION").d(result.toString())
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
        trigger: TaskingConfig.TaskConfig.Trigger,
        //teiUid: TaskingConfig.,
        sourceTieUid: String,
        programUid: String
    ): Boolean {
        val lhsValue = resolvedReference( trigger /*reference.condition.lhs.ref*/, sourceTieUid, trigger.condition.lhs.uid.toString(), programUid)
        val rhsValue = trigger.condition.rhs.value

        Timber.d(" CREATED_TASK Evaluating condition: lhs=$lhsValue, rhs=${trigger.condition.rhs.value}, op=${trigger.condition.op}")


        return when (trigger.condition.op) {
            "EQUALS" -> lhsValue == rhsValue
            "NOT_EQUALS" -> lhsValue != rhsValue
            else -> false
        }
    }

    private fun resolvedReference(
        trigger: TaskingConfig.TaskConfig.Trigger,
        //ref: String,//TaskingConfig.TaskConfig.Trigger,
        sourceTieUid: String,
        attrOrDataElementUid: String,
        programUid: String
    ): String? {
        val ref = trigger.condition.lhs.ref
        return when (ref) {
            "teiAttribute" -> {
                ref.let { uid ->
                    d2.trackedEntityModule().trackedEntityAttributeValues()
                        .byTrackedEntityInstance().eq(attrOrDataElementUid)
                        .byTrackedEntityAttribute().eq(uid)
                        .one().blockingGet()?.value()
                }
            }

            "eventData" -> {

                val enrollment = repository.getLatestEnrollment(/*attrOrDataElementUid*/ sourceTieUid, programUid) ?: return null
                val events = d2.eventModule().events()
                    .byEnrollmentUid().eq(enrollment.uid())
                    // .byProgramStageUid().eq(ref.ref)
                    .withTrackedEntityDataValues()
                    .blockingGet()

                return events.asSequence()
                    .flatMap { it.trackedEntityDataValues() ?: emptyList() }
                    .firstOrNull() { it.dataElement() == trigger.condition.lhs.uid }
                    ?.value()

                /*events.firstOrNull()?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == ref.uid }
                    ?.value()*/
            }

            "static" -> ref
            else -> null
        }
    }
}