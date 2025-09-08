package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import timber.log.Timber
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StatusEvaluator(
    private val statusAttributeUid: String,
    private val d2: D2,
    private val repository: TaskingRepository
) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateTaskStatusIfNeeded(
        teiUid: String,
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        programUid: String
    ) {
        try {
            val tei = d2.trackedEntityModule().trackedEntityInstances()
                .uid(teiUid)
                .blockingGet() ?: return

            if (tei.deleted() == true) return

            val enrollment = d2.enrollmentModule().enrollments()
                .byTrackedEntityInstance().eq(teiUid)
                .byProgram().eq(programUid)
                .blockingGet()
                .firstOrNull() ?: return

            if (enrollment.status() == EnrollmentStatus.COMPLETED) return

            val dueDateString = repository.calculateDueDate(taskConfig, teiUid, programUid) ?: return
            val dueDate = LocalDate.parse(dueDateString, java.time.format.DateTimeFormatter.ISO_DATE)
            val today = LocalDate.now()

            val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)

            val newStatus = when {
                today.isAfter(dueDate) -> "OVERDUE"
                today.isEqual(dueDate) -> "DUE_TODAY"
                daysUntilDue in 1..3 -> "DUE_SOON"
                else -> "OPEN"
            }

            val currentStatus = d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(statusAttributeUid, teiUid)
                .blockingGet()?.value()

            if (currentStatus != newStatus) {
                //repository.updateTaskStatus(teiUid, newStatus)
                repository.updateTaskAttrValue(repository.taskStatusAttributeUid, newStatus, teiUid)
            }
        } catch (e: Exception) {
            Timber.tag("StatusEvaluator").e(e, "Failed to update status for TEI $teiUid")
        }
    }
}
