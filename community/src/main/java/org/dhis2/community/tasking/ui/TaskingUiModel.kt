package org.dhis2.community.tasking.ui

import androidx.compose.ui.graphics.Color
import org.dhis2.community.tasking.models.Task
import org.hisp.dhis.mobile.ui.designsystem.component.ImageCardData
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import java.text.SimpleDateFormat
import java.util.*

data class TaskingUiModel(
    val task: Task,
    val orgUnit: String?,
    val repository: org.dhis2.community.tasking.repositories.TaskingRepository? = null
) {
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
            val colorString = repository?.getSourceProgramColor(task.sourceProgramUid)
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

    val displayProgramName: String get() = repository?.getProgramDisplayName(sourceProgramUid) ?: sourceProgramName
    val sourceTeiUid: String get() = task.sourceTeiUid
    val displayVillageName: String?
        get() {
            val teiSecondaryAttributeUid = repository?.getCachedConfig()?.programTasks?.firstOrNull()?.teiView?.teiSecondaryAttribute
            return if (!teiSecondary.isNullOrEmpty() && !teiSecondaryAttributeUid.isNullOrEmpty()) {
                repository?.getVillageDisplayName(teiSecondary, teiSecondaryAttributeUid)
            } else teiSecondary
        }


    // Progress properties
    val progressCurrent: Int get() = task.progressCurrent
    val progressTotal: Int get() = task.progressTotal
    val progressPercent: Int get() = if (progressTotal > 0) (progressCurrent * 100 / progressTotal) else 0
    val progressDisplay: String get() = "$progressCurrent of $progressTotal done ($progressPercent%)"
    // Color logic for progress
    val progressColor: Color
        get() = when (progressPercent) {
            0 -> SurfaceColor.Error
            in 1..24 -> SurfaceColor.Warning
            in 25..74 -> SurfaceColor.Primary
            in 75..99 -> SurfaceColor.Primary
            100 -> SurfaceColor.CustomGreen
            else -> SurfaceColor.Scrim
        }

    private fun parseDueDate(dueDate: String?): Date? {

        if (dueDate.isNullOrBlank()) {
            return null
        }

        // Check if this looks like an attribute ID (starts with letter)
        if (dueDate.matches(Regex("[a-zA-Z].*"))) {
            return null
        }

        val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
        if (!dateRegex.matches(dueDate)) {
            return null
        }

        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDate)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateStatus(apiStatus: String, dueDate: Date?): TaskingStatus {
        val statusLower = apiStatus.trim().lowercase(Locale.US)
        return when (statusLower) {
            "completed" -> TaskingStatus.COMPLETED
            "defaulted" -> TaskingStatus.DEFAULTED
            "open" -> {
                // Only "open" status gets date-based calculation
                if (dueDate == null) return TaskingStatus.OPEN
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val due = Calendar.getInstance().apply {
                    time = dueDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return when {
                    due.before(today) -> TaskingStatus.OVERDUE
                    due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            due.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> TaskingStatus.DUE_TODAY
                    due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            due.get(Calendar.DAY_OF_YEAR) in (today.get(Calendar.DAY_OF_YEAR) + 1)..(today.get(Calendar.DAY_OF_YEAR) + 3) -> TaskingStatus.DUE_SOON
                    else -> TaskingStatus.OPEN
                }
            }
            else -> TaskingStatus.OPEN
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
        SurfaceColor.Error
    )
}

// MetadataIconData for Avatar
data class MetadataIconData(
    val imageCardData: ImageCardData.IconCardData,
    val color: Color
)