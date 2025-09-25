package org.dhis2.community.tasking.models

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
            val taskTypeId: String,
            val name: String,
            val description: String,
            val trigger: Trigger,
            val period: Period,
            val priority: String,
            val completion: Completion,
            val singleIncomplete: Boolean,
            val anchorDate: String
        ) {
            interface HasConditions {
                val condition: List<Condition>
            }
            data class Trigger(
                val programName: String,
                val programUid: String,
                override val condition: List<Condition>
            ): HasConditions

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
                val dueInDays: Int
            )

        data class Completion(
            override val condition: List<Condition>
        ): HasConditions

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