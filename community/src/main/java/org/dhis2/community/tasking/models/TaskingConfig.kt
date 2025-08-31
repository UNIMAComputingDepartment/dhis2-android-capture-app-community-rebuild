package org.dhis2.community.tasking.models

data class TaskingConfig(
    val taskConfigs: List<TaskConfig>,
    val taskProgramConfig: List<TaskProgramConfig>
) {
    data class TaskProgramConfig(
        val name: String,
        val description: String,
        val programUid: String,
        val programName: String,
        val teiUid: String,
        val dueDate: String,
        val priority: String,
        val status: String = "OPEN"
    )

    data class TaskConfig(
        val taskTypeId: String,
        val name: String,
        val description: String,
        val trigger: Trigger,
        val period: Period,
        val priority: String,
        val completion: Completion,
        val singleIncomplete: Boolean,
        val teiView: TeiView,
        val programUid: String,
        val anchorDate: String
    ) {
        data class Trigger(
            val program: String,
            val condition: Condition
        )

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

        data class Period(
            val anchor: Reference,
            val dueIn: DueIn
        )

        data class DueIn(
            val days: Int
        )

        data class Completion(
            val condition: CompletionCondition
        )

        data class CompletionCondition(
            val op: String,
            val args: CompletionArgs
        )

        data class CompletionArgs(
            val program: String,
            val stage: String,
            val filter: List<Condition>
        )

        data class TeiView(
            val teiPrimaryAttribute: String,
            val teiSecondaryAttribute: String,
            val teiTertiaryAttribute: String
        )
    }
}