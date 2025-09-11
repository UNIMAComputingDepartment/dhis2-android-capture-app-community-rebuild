package org.dhis2.community.tasking.ui

import android.util.Log
import androidx.compose.ui.graphics.Color
import org.dhis2.community.tasking.models.Task
import org.hisp.dhis.mobile.ui.designsystem.component.ImageCardData
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.text.SimpleDateFormat
import java.util.*
import timber.log.Timber

data class TaskingUiModel(
    val task: Task,
    val orgUnit: String?,
    val repository: org.dhis2.community.tasking.repositories.TaskingRepository
) {
    init {
        Log.d("TaskingUiModel", "TaskingUiModel created: teiUid=${task.teiUid}, sourceProgramUid=${task.sourceProgramUid}, sourceEnrollmentUid=${task.sourceEnrollmentUid}, sourceProgramName=${task.sourceProgramName}, dueDate=${task.dueDate}, priority=${task.priority}, status=${task.status}")
    }

    // Delegate properties from Task
    val taskName: String get() = task.name
    val taskDescription: String get() = task.description
    val sourceProgramUid: String get() = task.sourceProgramUid
    val sourceProgramName: String get() = task.sourceProgramName
    val sourceEnrollmentUid: String get() = task.sourceEnrollmentUid
    val teiUid: String get() = task.teiUid
    val teiPrimary: String get() = task.teiPrimary
    val teiSecondary: String get() = task.teiSecondary
    val teiTertiary: String get() = task.teiTertiary
    val dueDate: Date? get() = parseDueDate(task.dueDate)
    val priority: TaskingPriority get() = TaskingPriority.fromLabel(task.priority)
    val status: TaskingStatus get() = calculateStatus(task.status, dueDate)
    val metadataIconData: MetadataIconData
        get() {
            val iconName = task.iconNane?.takeIf { it.isNotBlank() }
            val iconRes = iconName?.let { "dhis2_" + it } ?: "dhis2_default"
            val colorString = repository.getSourceProgramColor(task.sourceProgramUid)
            val color = colorString?.takeIf { it.isNotBlank() }?.let {
                try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (e: Exception) {
                    SurfaceColor.Primary
                }
            } ?: SurfaceColor.Primary
            return MetadataIconData(
                imageCardData = ImageCardData.IconCardData(
                    uid = teiUid,
                    label = taskName,
                    iconRes = iconRes,
                    iconTint = color
                ),
                color = color
            )
        }

    val displayProgramName: String get() = repository.getProgramDisplayName(sourceProgramUid) ?: sourceProgramName
    val isNavigable: Boolean get() = repository.isValidTeiEnrollment(teiUid, sourceProgramUid, sourceEnrollmentUid)

    private fun parseDueDate(dueDate: String?): Date? {
        Log.d("TaskingUiModel", "parseDueDate called with: '$dueDate'")

        if (dueDate.isNullOrBlank()) {
            Log.d("TaskingUiModel", "Due date is null or blank")
            return null
        }

        // Check if this looks like an attribute ID (starts with letter)
        if (dueDate.matches(Regex("[a-zA-Z].*"))) {
            Log.e("TaskingUiModel", "ERROR: This looks like an attribute ID, not a date: $dueDate")
            return null
        }

        val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
        if (!dateRegex.matches(dueDate)) {
            Log.e("TaskingUiModel", "DueDate value is not a valid date format: $dueDate")
            return null
        }

        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDate)
        } catch (e: Exception) {
            Log.e("TaskingUiModel", "Error parsing dueDate: $dueDate", e)
            null
        }
    }

    private fun calculateStatus(apiStatus: String, dueDate: Date?): TaskingStatus {
        Log.d("TaskingUiModel", "calculateStatus called with apiStatus: $apiStatus, dueDate: $dueDate")
        return when (apiStatus) {
            "COMPLETED" -> TaskingStatus.COMPLETED
            "DEFAULTED" -> TaskingStatus.DEFAULTED
            "OVERDUE" -> TaskingStatus.OVERDUE
            "DUE_TODAY" -> TaskingStatus.DUE_TODAY
            "DUE_SOON" -> TaskingStatus.DUE_SOON
            "OPEN" -> TaskingStatus.OPEN
            else -> {
                // Fallback to date logic if status is unknown
                if (dueDate == null) return TaskingStatus.OPEN
                val today = Calendar.getInstance()
                val due = Calendar.getInstance().apply { time = dueDate }
                when {
                    due.before(today) -> TaskingStatus.OVERDUE
                    due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            due.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> TaskingStatus.DUE_TODAY
                    due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            due.get(Calendar.DAY_OF_YEAR) <= today.get(Calendar.DAY_OF_YEAR) + 3 -> TaskingStatus.DUE_SOON
                    else -> TaskingStatus.OPEN
                }
            }
        }
    }
}

enum class TaskingPriority(
    val label: String,
    val color: Color,
) {
    HIGH(
        "High Priority",
        SurfaceColor.Error
    ),
    MEDIUM(
        "Medium Priority",
        SurfaceColor.Warning
    ),
    LOW(
        "Low Priority",
        SurfaceColor.Primary
    );

    companion object {
        fun fromLabel(label: String): TaskingPriority {
            return when (label.lowercase()) {
                "high" -> HIGH
                "medium" -> MEDIUM
                "low" -> LOW
                else -> when {
                    label.contains("high", ignoreCase = true) -> HIGH
                    label.contains("medium", ignoreCase = true) -> MEDIUM
                    else -> LOW
                }
            }
        }
    }
}

enum class TaskingStatus(
    val label: String,
    val color: Color
) {
    OPEN(
        "Open",
        SurfaceColor.Primary
    ),
    DUE_TODAY(
        "Due Today",
        SurfaceColor.Warning
    ),
    DUE_SOON(
        "Due Soon",
        SurfaceColor.Warning
    ),
    OVERDUE(
        "Overdue",
        SurfaceColor.Error
    ),
    COMPLETED(
        "Completed",
        SurfaceColor.CustomGreen
    ),
    DEFAULTED(
        "Defaulted",
        TextColor.OnSurfaceVariant
    )
}

// MetadataIconData for Avatar
data class MetadataIconData(
    val imageCardData: ImageCardData.IconCardData,
    val color: Color
)