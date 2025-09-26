package org.dhis2.community.tasking.models

data class Task(
    val name: String,
    val description: String,
    val sourceProgramUid: String,
    val sourceEnrollmentUid: String,
    val sourceProgramName: String,
    val sourceTeiUid: String,
    val sourceEventUid: String?,
    val teiUid: String, //source teiUid
    val teiPrimary: String,
    val teiSecondary: String,
    val teiTertiary: String,
    val dueDate: String,
    val priority: String,
    val status: String = "OPEN",
    val iconNane : String?,
    val progress: Float = 0.7f
)