package org.dhis2.community.tasking.models

data class Task(
    val name: String,
    val description: String,
    val programUid: String,
    val programName: String,
    val teiUid: String,
    val teiPrimary: String,
    val teiSecondary: String,
    val teiTertiary: String,
    val dueDate: String,
    val priority: String,
    val status: String = "OPEN"
)