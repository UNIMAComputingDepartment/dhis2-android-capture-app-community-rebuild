package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.EvaluationResult
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

abstract class TaskingEvaluator(
    private val repository: TaskingRepository
) {
    @RequiresApi(Build.VERSION_CODES.O)
    internal fun calculateDueDate(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
    ):  String?{
        if (taskConfig.period.anchor.uid.isNullOrBlank() || teiUid.isBlank()) {
            val date =  Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            return date.plusDays(taskConfig.period.dueInDays.toLong()).toString()
        }

        val dateOfBirthValue : String? = repository.d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(taskConfig.period.anchor.uid, teiUid)
            .blockingGet()?.value()

        if (dateOfBirthValue.isNullOrBlank()) {
            val today = Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            return today.plusDays(taskConfig.period.dueInDays.toLong()).toString()
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = dateFormat.parse(dateOfBirthValue)

        val dateOfBirth = birthDate?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

        return dateOfBirth?.plusDays(taskConfig.period.dueInDays.toLong()).toString()
    }

    internal fun evaluateTriggerConditions(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String
    ): Boolean {
        val result = taskConfig.trigger.condition.map { cond ->
            val lhsValue =
                this.resolvedReference(taskConfig.trigger, teiUid, cond.lhs.uid.toString(), programUid)
            val rhsValue = cond.rhs.value

            when (cond.op) {
                "EQUALS" -> lhsValue == rhsValue
                "NOT_EQUALS" -> lhsValue != rhsValue
                "NOT_NULL" -> !lhsValue.isNullOrEmpty()
                "NULL" -> lhsValue.isNullOrEmpty()
                else -> false
            }
        }

        return result.any { it }
    }


    internal fun evaluateCompletionConditions(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String
    ): List<EvaluationResult> {
        val result = taskConfig.completion.condition.map { cond ->
            val lhsValue =
                this.resolvedReference(
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

    internal fun getTeiAttributes(
        tieUid: String,
        tieView: TaskingConfig.ProgramTasks.TeiView
    ): Triple<String, String, String> {
        fun getAttr(uid: String) =
            repository.d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tieUid)
                .byTrackedEntityAttribute().eq(uid)
                .one().blockingGet()?.value() ?: ""

        return Triple(
            getAttr(tieView.teiPrimaryAttribute),
            getAttr(tieView.teiSecondaryAttribute),
            getAttr(tieView.teiTertiaryAttribute)
        )
    }

    fun resolvedReference(
        trigger: TaskingConfig.ProgramTasks.TaskConfig.HasConditions,
        teiUid: String,
        attrOrDataElementUid: String,
        programUid: String
    ): String? {
        return trigger.condition.firstNotNullOfOrNull { cond ->

            val enrollment = repository.getLatestEnrollment(teiUid, programUid)
                ?: return@firstNotNullOfOrNull null

            val events = repository.d2.eventModule().events()
                .byEnrollmentUid().eq(enrollment.uid())
                .withTrackedEntityDataValues()
                .blockingGet()
            when (cond.lhs.ref) {
                "teiAttribute" -> repository.d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byTrackedEntityAttribute().eq(attrOrDataElementUid)
                    .one().blockingGet()?.value()

                "eventData" -> {


                    /**/

                    val latestEvent = events
                        .maxByOrNull { it.created()?: it.eventDate()?: Date(0) } // choose your ordering

                    // From the latest event, get the value of the desired data element
                    latestEvent
                        ?.trackedEntityDataValues()
                        ?.firstOrNull { it.dataElement() == attrOrDataElementUid }
                        ?.value()
                }

                "allEventsData" -> {
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
}