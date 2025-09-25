package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.event.Event
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
                "EQUALS" -> rhsValue == lhsValue
                "NOT_EQUALS" -> rhsValue != lhsValue
                "NOT_NULL" -> !lhsValue.isNullOrEmpty()
                "NULL" -> lhsValue.isNullOrEmpty()
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
            "teiAttribute" -> repository.d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(teiUid)
                .byTrackedEntityAttribute().eq(reference.uid)
                .one().blockingGet()?.value()

            "eventData" -> {
                val latestEvent = repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid)
                latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
            }

            "allEventsData" -> {
                val latestEvent = repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid)
                latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
            }

            "static" -> reference.uid
            else -> null
        }
    }
}