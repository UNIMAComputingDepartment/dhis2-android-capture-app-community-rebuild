package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import timber.log.Timber
import java.util.Date
import kotlin.collections.forEach

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
        require(config.programTasks.isNotEmpty()) { "Tasking Config is Empty" }

        val configsForProgram =
            config.programTasks.firstOrNull() { it.programUid == programUid } ?: return emptyList()


        val results = mutableListOf<EvaluationResult>()

        val ties =
            repository.getAllTrackedEntityInstances(programUid, sourceTieUid, sourceTieOrgUnit)
        ties.forEach { tei ->
            configsForProgram.taskConfigs.forEach { taskConfig ->
                // Evaluate all conditions and return a list of results
                val evalResults = evaluateTriggerConditions(taskConfig = taskConfig, tei.uid(), programUid)
                evalResults.filter { it.isTriggered }.forEach { result ->
                    val dueDate = repository.dueDateCalculation(taskConfig, sourceTieUid)
                        ?: return@forEach
                    val tieAttrs = getTieAttributes(tei.uid(), configsForProgram.teiView)
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

    protected fun evaluateTriggerConditions(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String
    ): List<EvaluationResult> {
        val result = taskConfig.trigger.condition.map { cond ->
            val lhsValue =
                repository.resolvedReference(taskConfig.trigger, teiUid, cond.lhs.uid.toString(), programUid)
            val rhsValue = cond.rhs.value

            when (cond.op) {
                "EQUALS" -> lhsValue == rhsValue
                "NOT_EQUALS" -> lhsValue != rhsValue
                "NOT_NULL" -> !lhsValue.isNullOrEmpty()
                "NULL" -> lhsValue.isNullOrEmpty()
                else -> false
            }
        }

        val isTriggered = result.any { it }

        val results =  if (isTriggered) {
            listOf(
                EvaluationResult(
                    taskingConfig = taskConfig,
                    teiUid = teiUid,
                    programUid = programUid,
                    isTriggered = true,
                    dueDate = null, // to be filled later
                    tieAttrs = Triple("", "", ""),
                    orgUnit = null
                )
            )
        } else emptyList()

        return  results
    }


    private fun evaluateCompletionConditions(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String
    ): List<EvaluationResult> {
        val result = taskConfig.completion.condition.map { cond ->
            val lhsValue =
                repository.resolvedReference(
                    taskConfig.completion,
                    teiUid,
                    cond.lhs.uid.toString(),
                    programUid
                )
            val rhsValue = cond.rhs.value

            when (cond.op) {
                "EQUALS" -> lhsValue == rhsValue
                "NOT_EQUALS" -> lhsValue != rhsValue
                "NOT_NULL" -> !lhsValue.isNullOrEmpty()
                "NULL" -> lhsValue.isNullOrEmpty()
                else -> false
            }
        }

        val isTriggered = result.all { it }

        val results = if (isTriggered) {
            listOf(
                EvaluationResult(
                    taskingConfig = taskConfig,
                    teiUid = teiUid,
                    programUid = programUid,
                    isTriggered = true,
                    dueDate = null, // to be filled later
                    tieAttrs = Triple("", "", ""),
                    orgUnit = null
                )
            )
        } else emptyList()

        return results
    }

    private fun getTieAttributes(
        tieUid: String,
        tieView: TaskingConfig.ProgramTasks.TeiView
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

    fun taskCompletion(
        tasks: List<Task>,
        sourceProgramEnrollmentUid: String,
        sourceProgramUid: String,
        sourceTeiUid: String?
    ) {


        val taskConf = repository.getTaskingConfig()
        require(taskConf.programTasks.isNotEmpty()) { "Task Config is Empty" }

        val configForPg = taskConf.programTasks
            .filter { it.programUid == sourceProgramUid }
            .flatMap { it.taskConfigs }

        if (configForPg.isEmpty()) return

        val taskProgramUid = taskConf.taskProgramConfig.firstOrNull()?.programUid


        tasks.filter{it.sourceProgramUid == sourceProgramUid}
            .forEach { task ->

            val taskConfig = configForPg.firstOrNull() { it.name == task.name }
                if (taskConfig == null){
                    return@forEach
                }

            if (task.sourceEnrollmentUid == sourceProgramEnrollmentUid) {
                // Check trigger first
                val triggerResults = evaluateTriggerConditions(
                    taskConfig = taskConfig,
                    teiUid = sourceTeiUid!!,
                    programUid = sourceProgramUid
                )
                val isTriggered = triggerResults.any { it.isTriggered }
                if (isTriggered) {
                    // Only check completion if trigger is still met
                    val completionResults = evaluateCompletionConditions(
                        taskConfig = taskConfig,
                        teiUid = sourceTeiUid,
                        programUid = sourceProgramUid)
                    if(completionResults.any{it.isTriggered}){
                        repository.updateTaskAttrValue(
                            repository.taskStatusAttributeUid,
                            "completed",
                            task.teiUid
                        )
                        val taskTeiEnrollmentUid = d2.enrollmentModule().enrollments()
                            .byTrackedEntityInstance().eq(task.teiUid)
                            .byProgram().eq(taskProgramUid)
                            .byStatus().eq(EnrollmentStatus.ACTIVE)
                            .one().blockingGet()?.uid()
                        if (taskTeiEnrollmentUid != null) {
                            d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid)
                                .setStatus(EnrollmentStatus.COMPLETED)
                            d2.enrollmentModule().enrollments().uid(taskTeiEnrollmentUid)
                                .setCompletedDate(Date())
                        }   else{
                            Timber.d("No active enrollment")
                        }
                    }
                }

            } else null
        }
    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    fun evaluateDefaultingConditions(
//        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
//        teiUid: String,
//        programUid: String,
//        enrollmentUid: String
//    ): List<EvaluationResult> {
//        val result = taskConfig.trigger.condition.map { cond ->
//            val lhsValue = repository.resolvedReference(taskConfig.trigger, teiUid, cond.lhs.uid.toString(), programUid)
//            val rhsValue = repository.resolvedReference(taskConfig.trigger, teiUid, cond.rhs.uid.toString(), programUid)
//
//            // Defaulting logic:
//            // 1. If lhsValue is null or empty (trigger no longer met)
//            // 2. But rhsValue exists (task still present for enrollment)
//            lhsValue.isNullOrEmpty() && !rhsValue.isNullOrEmpty()
//        }
//
//        val isDefaulted = result.any { it }
//
//        val results = if (isDefaulted) {
//            listOf(
//                EvaluationResult(
//                    taskingConfig = taskConfig,
//                    teiUid = teiUid,
//                    programUid = programUid,
//                    isTriggered = true,
//                    dueDate = null,
//                    tieAttrs = Triple("", "", ""),
//                    orgUnit = null
//                )
//            )
//        } else emptyList()
//
//        return results
//    }
//
//    @RequiresApi(Build.VERSION_CODES.O)
//    fun taskDefaulting(
//        tasks: List<Task>,
//        sourceProgramEnrollmentUid: String,
//        sourceProgramUid: String,
//        sourceTeiUid: String?
//    ) {
//        val taskConf = repository.getTaskingConfig()
//        require(taskConf.programTasks.isNotEmpty()) { "Task Config is Empty" }
//
//        val configForPg = taskConf.programTasks
//            .filter { it.programUid == sourceProgramUid }
//            .flatMap { it.taskConfigs }
//
//        if (configForPg.isEmpty()) return
//
//        val taskProgramUid = taskConf.taskProgramConfig.firstOrNull()?.programUid
//
//        tasks.filter { it.sourceProgramUid == sourceProgramUid }
//            .forEach { task ->
//                val taskConfig = configForPg.firstOrNull { it.name == task.name }
//                if (taskConfig == null) {
//                    return@forEach
//                }
//
//                if (task.sourceEnrollmentUid == sourceProgramEnrollmentUid) {
//                    val condition = evaluateDefaultingConditions(
//                        taskConfig = taskConfig,
//                        teiUid = sourceTeiUid!!,
//                        programUid = sourceProgramUid,
//                        enrollmentUid = sourceProgramEnrollmentUid
//                    )
//
//                    // Defaulting criteria: (1) trigger no longer met, (2) task exists for enrollment
//                    if (condition.any { it.isTriggered }) {
//                        repository.updateTaskAttrValue(
//                            repository.taskStatusAttributeUid,
//                            "defaulted",
//                            task.teiUid
//                        )
//                    }
//                }
//            }
//    }




    /*private fun resolvedReference(
        completion: TaskingConfig.ProgramTasks.TaskConfig.Completion,
        teiUid: String,
        attrOrDataElementUid: String,
        programUid: String
    ): String? {
        return completion.condition.firstNotNullOfOrNull { cond ->
            Timber.d(cond.lhs.ref)
             when (cond.lhs.ref) {

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

                "teiAttribute" -> d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byTrackedEntityAttribute().eq(attrOrDataElementUid)
                    .one().blockingGet()?.value()

                "static" -> cond.lhs.uid
                else -> {
                    Timber.d("default")
                    null
                }
            }
        }
    }*/
}