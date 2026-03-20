package org.dhis2.community.tasking.engine

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
        eventUid: String? = null,
        secondaryProgramUid: String? = null
    ): List<Boolean> {
        return conditions.condition.map { cond ->
            val lhsValue = this.resolvedReference(cond.lhs, teiUid, programUid, eventUid, secondaryProgramUid)
            val rhsValue = this.resolvedReference(cond.rhs, teiUid, programUid, eventUid, secondaryProgramUid)

            // Injecting logs to see exactly what is being compared
            Log.d("TASK_DEBUG", "EVALUATING Condition: [ref=${cond.lhs.ref}, uid=${cond.lhs.uid}] LHS='$lhsValue' ${cond.op} RHS='$rhsValue'")

            // BIRTH WEIGHT SPECIFIC LOGGING
            when (cond.lhs.uid) {
                "NMdsnU5SaSF" -> {
                    Log.d("BIRTH_WEIGHT_DEBUG", "╔════════════════════════════════════════════╗")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ BIRTH WEIGHT CATEGORY CHECK                ║")
                    Log.d("BIRTH_WEIGHT_DEBUG", "╠════════════════════════════════════════════╣")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ TEI: $teiUid")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Birth Weight Category (LHS): '$lhsValue'")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Expected Weight Category (RHS): '$rhsValue'")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Operator: ${cond.op}")
                    Log.d("BIRTH_WEIGHT_DEBUG", "╚════════════════════════════════════════════╝")
                }
                "vGBbxUdRFlG" -> {
                    Log.d("BIRTH_WEIGHT_DEBUG", "╔════════════════════════════════════════════╗")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ VISIT NUMBER CHECK                         ║")
                    Log.d("BIRTH_WEIGHT_DEBUG", "╠════════════════════════════════════════════╣")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Visit Number (LHS): '$lhsValue'")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Expected Visit (RHS): '$rhsValue'")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Operator: ${cond.op}")
                    Log.d("BIRTH_WEIGHT_DEBUG", "╚════════════════════════════════════════════╝")
                }
                "Ybo4mhfpt9O" -> {
                    Log.d("BIRTH_WEIGHT_DEBUG", "╔════════════════════════════════════════════╗")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ TYPE OF VISIT CHECK                        ║")
                    Log.d("BIRTH_WEIGHT_DEBUG", "╠════════════════════════════════════════════╣")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Type of Visit (LHS): '$lhsValue'")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Expected Type (RHS): '$rhsValue'")
                    Log.d("BIRTH_WEIGHT_DEBUG", "║ Operator: ${cond.op}")
                    Log.d("BIRTH_WEIGHT_DEBUG", "╚════════════════════════════════════════════╝")
                }
            }

            val result = when (cond.op) {
                "EQUALS" -> rhsValue == lhsValue
                "NUM_EQUAL" -> rhsValue?.toDoubleOrNull() == lhsValue?.toDoubleOrNull()
                "NOT_EQUALS" -> rhsValue != lhsValue
                "NOT_NULL" -> !lhsValue.isNullOrEmpty()
                "NULL" -> lhsValue.isNullOrEmpty()
                "GREATER_THAN" -> {
                    val lhs = lhsValue?.toDoubleOrNull()
                    val rhs = rhsValue?.toDoubleOrNull()
                    rhs != null && lhs != null && lhs > rhs
                }
                "GREATER_THAN_OR_EQUALS" -> {
                    val lhs = lhsValue?.toDoubleOrNull()
                    val rhs = rhsValue?.toDoubleOrNull()
                    if (lhs != null && rhs != null) lhs >= rhs else lhsValue != null && rhsValue != null && lhsValue >= rhsValue
                }
                "LESS_THAN_OR_EQUALS" -> {
                    val lhs = lhsValue?.toDoubleOrNull()
                    val rhs = rhsValue?.toDoubleOrNull()
                    if (lhs != null && rhs != null) lhs <= rhs else lhsValue != null && rhsValue != null && lhsValue <= rhsValue
                }
                "LESS_THAN" -> {
                    val lhs = lhsValue?.toDoubleOrNull()
                    val rhs = rhsValue?.toDoubleOrNull()
                    if (lhs != null && rhs != null) lhs < rhs else lhsValue != null && rhsValue != null && lhsValue < rhsValue
                }
                else -> false
            }
            
            Log.d("TASK_DEBUG", "RESULT of condition: $result")
            
            // Log result for birth weight conditions
            if (cond.lhs.uid in listOf("NMdsnU5SaSF", "vGBbxUdRFlG", "Ybo4mhfpt9O")) {
                Log.d("BIRTH_WEIGHT_DEBUG", "Result: ${if (result) "✓ PASS" else "✗ FAIL"}")
            }
            
            result
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
        eventUid: String? = null,
        secondaryProgramUid: String?
    ): String? {

        val enrollment = repository.getLatestEnrollment(teiUid, programUid)
            ?: secondaryProgramUid?.let { repository.getLatestEnrollment(teiUid, it) } ?: return null

        if (reference.uid.isNullOrBlank())
            return reference.value.toString()

        val result = when (reference.ref) {
            "attribute" -> {
                val attrValue = repository.d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byTrackedEntityAttribute().eq(reference.uid)
                    .one().blockingGet()?.value()
                
                // Birth weight category logging
                if (reference.uid == "NMdsnU5SaSF") {
                    Log.d("BIRTH_WEIGHT_DEBUG", "🔍 Fetched Birth Weight Category: '$attrValue'")
                }
                attrValue
            }

            "eventData" -> {
                val latestEvent = repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid)
                val eventValue = latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
                
                // Visit number logging
                if (reference.uid == "vGBbxUdRFlG") {
                    Log.d("BIRTH_WEIGHT_DEBUG", "🔍 Fetched Visit Number: '$eventValue'")
                }
                // Type of visit logging
                if (reference.uid == "Ybo4mhfpt9O") {
                    Log.d("BIRTH_WEIGHT_DEBUG", "🔍 Fetched Type of Visit: '$eventValue'")
                }
                eventValue
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
        
        Log.d("TASK_DEBUG", "FETCHED Data for [ref=${reference.ref}, uid=${reference.uid}] -> Value='$result'")
        return result
    }
}