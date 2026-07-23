package org.dhis2.community.tasking.models

import android.R

data class TaskingConfig(
    val programTasks: List<ProgramTasks>,
    val taskProgramConfig: List<TaskProgramConfig>
) {
    data class TaskProgramConfig(
        val taskNameUid: String,
        val description: String,
        val programUid: String,
        val programName: String,
        val teiTypeUid: String,
        val dueDateUid: String,
        val priorityUid: String,
        val statusUid: String,
        val taskPrimaryAttrUid: String,
        val taskSecondaryAttrUid: String,
        val taskTertiaryAttrUid: String,
        val taskSourceProgramUid: String,
        val taskSourceEnrollmentUid: String,
        val taskSourceTeiUid: String,
        val taskSourceEventUid: String?,
        val taskProgressUid: String?
    )

    data class ProgramTasks(
        val taskConfigs: List<TaskConfig>,
        val programUid: String,
        val programName: String,
        val teiView: TeiView
    ) {
        data class TeiView(
            val teiPrimaryAttribute: String,
            val teiSecondaryAttribute: String,
            val teiTertiaryAttribute: String
        )

        data class TaskConfig(
            val frequency : String ? = null,
            val hasEvent : Boolean? = false,
            val taskTypeId: String,
            val name: String,
            val description: String,
            val trigger: Trigger,
            val period: Period,
            val priority: String = "medium",
            val completion: Completion,
            val singleIncomplete: Boolean = false,
            val anchorDate: String
        ) {

            interface HasConditions {
                val condition: List<Condition>
            }

            data class Trigger(
                val programName: String,
                val programUid: String,
                val programStageUid: String? = null,
                val combination: String,
                override val condition: List<Condition>
            ) : HasConditions

            data class Condition(
                val op: String,
                val lhs: Reference,
                val rhs: Reference
            )

            data class Reference(
                val ref: String,
                val uid: String? = null,
                val value: Any? = null,
                val type: String? = null,
                val fn: String? = null
            )

            data class Schedule(
                val type: String? = null,
                val ref: String? = null,
                val repeat: Boolean ? = false
            )


            data class Period(
                // Evaluated in order; the due date of the first rule whose condition matches is used.
                // Nullable (rather than defaulting to emptyList()) because Gson deserializes this class
                // via reflection, bypassing the Kotlin constructor — a JSON period with no "rules" key
                // ends up with a real null here, not the declared default. Callers must use .orEmpty().
                val rules: List<DueDateRule>? = null,
                // Used when no rule matches (or none are configured) — same shape as the previous flat Period.
                val default: DueDateSpec,
                // If the resolved due date would land before the reference (triggering event) date,
                // the safety clamp normally falls back to the reference date itself. When set, it
                // falls back to referenceDate + this many days instead — e.g. "due 60 days before
                // EDD, but if that's already in the past, due 14 days from the visit" without needing
                // any extra lookup: the clamp already detects "naive due date is in the past" using
                // data (default anchor, reference date) that's already been resolved.
                val fallbackDueInDays: Int? = null
            )

            data class DueDateRule(
                val combination: String? = null,
                override val condition: List<Condition>,
                val dueDate: DueDateSpec
            ) : HasConditions

            data class DueDateSpec(
                val anchor: Reference,
                val dueInDays: Int
            )

            data class Completion(
                val combination: String? = null,
                override val condition: List<Condition>
            ) : HasConditions

            data class CompletionCondition(
                val op: String,
                val args: List<CompletionArgs>
            )

            data class CompletionArgs(
                val program: String,
                val stage: String,
                val filter: List<Condition>
            )

        }
    }
}