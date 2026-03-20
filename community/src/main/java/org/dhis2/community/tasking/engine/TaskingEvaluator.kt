package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.utils.Constants
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
        programUid : String,
    ):  String?{

        val today = Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

        if (taskConfig.period.anchor.uid.isNullOrBlank() || teiUid.isBlank()) {
            val date =  Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            return date.plusDays(taskConfig.period.dueInDays.toLong()).toString()
        }

        val enrollmentUid = repository.getLatestEnrollment(teiUid, programUid)


        val periodAnchor = repository.getLatestEvent(programUid,
            taskConfig.period.anchor.uid,
            enrollmentUid.toString(),
            null)
            ?.trackedEntityDataValues()
            ?.firstOrNull { it.dataElement() == taskConfig.period.anchor.uid }
            ?.value()

        /*val periodAnchor : String? = repository.d2.trackedEntityModule()
            .trackedEntityAttributeValues()
            .value(taskConfig.period.anchor.uid, teiUid)
            .blockingGet()
            ?.value()*/

        if (periodAnchor.isNullOrBlank()) {
            return today.plusDays(taskConfig.period.dueInDays.toLong()).toString()
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val anchorDate = dateFormat.parse(periodAnchor)
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?: return null

        //val formatedPeriodAnchorValue = anchorDate?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

        //val dueDate = anchorDate.plusDays(taskConfig.period.dueInDays.toLong())

        return when (taskConfig.period.anchor.ref){
            //"" -> dueDate.toString()
            //"DIFF" -> java.time.temporal.ChronoUnit.DAYS.between(anchorDate,dueDate).toString()
            "PAST" -> anchorDate.minusDays(taskConfig.period.dueInDays.toLong()).toString()
            else -> anchorDate.plusDays(taskConfig.period.dueInDays.toLong()).toString()

        }
        //return formatedPeriodAnchorValue?.plusDays(taskConfig.period.dueInDays.toLong()).toString()
    }

    internal fun evaluateConditions(
        conditions: TaskingConfig.ProgramTasks.TaskConfig.HasConditions,
        teiUid: String,
        programUid: String,
        eventUid: String? = null
    ): List<Boolean> {
        return conditions.condition.map { cond ->
            val lhsValue = this.resolvedReference(cond.lhs, teiUid, programUid, eventUid)
            val rhsValue = this.resolvedReference(cond.rhs, teiUid, programUid, eventUid)

            when (cond.op) {
                Constants.EQUALS -> rhsValue == lhsValue
                Constants.NUM_EQUALS -> rhsValue?.toDouble() == lhsValue?.toDouble()
                Constants.NOT_EQUAL -> rhsValue != lhsValue
                Constants.NOT_NULL -> !lhsValue.isNullOrEmpty()
                Constants.NULL -> lhsValue.isNullOrEmpty()
                Constants.GREATER_THAN -> {
                    val lhs = (lhsValue?.toDouble() as? Number)?.toDouble()
                    val rhs = (rhsValue?.toDouble() as? Number)?.toDouble()
                    rhs != null && lhs != null && lhs > rhs
                }
                else -> false
            }
        }
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
        reference: TaskingConfig.ProgramTasks.TaskConfig.Reference,
        teiUid: String,
        programUid: String,
        eventUid: String? = null
    ): String? {

        val enrollment = repository.getLatestEnrollment(teiUid, programUid)
            ?: return null

        if (reference.uid.isNullOrBlank())
            return reference.value.toString()

        return when (reference.ref) {
            Constants.TEI_ATTRIBUTE -> repository.d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(teiUid)
                .byTrackedEntityAttribute().eq(reference.uid)
                .one().blockingGet()?.value()

            Constants.EVENT_DATA -> {
                val latestEvent = repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid)
                latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
            }

            Constants.ALL_EVENTS_DATA -> {
                val latestEvent = repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid)
                latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
            }

            Constants.STATIC -> reference.uid
            else -> null
        }
    }
}