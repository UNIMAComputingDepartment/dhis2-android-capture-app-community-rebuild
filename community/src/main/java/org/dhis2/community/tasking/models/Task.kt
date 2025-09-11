package org.dhis2.community.tasking.models

import android.util.Log

data class EvaluationResult(
    val taskingConfig: TaskingConfig.ProgramTasks.TaskConfig,
    val teiUid: String, //source tei
    val programUid: String,
    val isTriggered: Boolean,
    val dueDate: String? = null,
    val tieAttrs: Triple<String, String, String>? = null,
    val orgUnit: String?
)

data class Task(
    val name: String,
    val description: String,
    val sourceProgramUid: String,
    val sourceEnrollmentUid: String,
    val sourceProgramName: String,
    val teiUid: String, //source teiUid
    val teiPrimary: String,
    val teiSecondary: String,
    val teiTertiary: String,
    val dueDate: String,
    val priority: String,
    val status: String = "OPEN",
    val iconNane : String?
) {
    init {
        Log.d("Task", "Task created: name=$name, description=$description, sourceProgramUid=$sourceProgramUid, sourceEnrollmentUid=$sourceEnrollmentUid, sourceProgramName=$sourceProgramName, teiUid=$teiUid, teiPrimary=$teiPrimary, teiSecondary=$teiSecondary, teiTertiary=$teiTertiary, dueDate=$dueDate, priority=$priority, status=$status, iconNane=$iconNane")
    }
}
