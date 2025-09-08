package org.dhis2.community.tasking.ui

import androidx.compose.ui.graphics.Color
import org.dhis2.community.tasking.models.Task
import org.hisp.dhis.mobile.ui.designsystem.component.ImageCardData
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.text.SimpleDateFormat
import java.util.*

data class TaskingUiModel(
    val task: Task,
) {
    // Delegate properties from Task
    val taskName: String get() = task.name
    val taskDescription: String get() = task.description
    val sourceProgramUid: String get() = task.sourceProgramUid
    val sourceProgramName: String get() = task.sourceProgramName
    val teiUid: String get() = task.teiUid
    val teiPrimary: String get() = task.teiPrimary
    val teiSecondary: String get() = task.teiSecondary
    val teiTertiary: String get() = task.teiTertiary
    val dueDate: Date? get() = parseDueDate(task.dueDate)
    val priority: TaskingPriority get() = TaskingPriority.fromLabel(task.priority)
    val status: TaskingStatus get() = calculateStatus(task.status, dueDate)
    val programType: ProgramType get() = ProgramType.fromUid(task.sourceProgramUid)
    val metadataIconData: MetadataIconData
        get() = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = teiUid,
                label = taskName,
                iconRes = programType.getMetadataIcon(),
                iconTint = programType.getMetadataColor()
            ),
            color = programType.getMetadataColor()
        )

    private fun parseDueDate(dueDate: String?): Date? {
        return try {
            if (dueDate.isNullOrBlank()) null
            else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dueDate)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateStatus(apiStatus: String, dueDate: Date?): TaskingStatus {
        return when (apiStatus) {
            "COMPLETED" -> TaskingStatus.COMPLETED
            "DEFAULTED" -> TaskingStatus.DEFAULTED
            "OVERDUE" -> TaskingStatus.OVERDUE
            "DUE_TODAY" -> TaskingStatus.DUE_TODAY
            "DUE_SOON" -> TaskingStatus.DUE_SOON
            "OPEN" -> TaskingStatus.UPCOMING
            else -> {
                // Fallback to date logic if status is unknown
                if (dueDate == null) return TaskingStatus.UPCOMING
                val today = Calendar.getInstance()
                val due = Calendar.getInstance().apply { time = dueDate }
                when {
                    due.before(today) -> TaskingStatus.OVERDUE
                    due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            due.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> TaskingStatus.DUE_TODAY
                    due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            due.get(Calendar.DAY_OF_YEAR) <= today.get(Calendar.DAY_OF_YEAR) + 3 -> TaskingStatus.DUE_SOON
                    else -> TaskingStatus.UPCOMING
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
    UPCOMING(
        "Upcoming",
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

// ProgramType enum for mapping
enum class ProgramType(val uid: String, val label: String) {
    EPI("IpHINAT79UW", "Expanded Programme on Immunization - EPI"),
    WOMAN("WSGAb5XwJ3Y", "CBMNC - Woman Program"),
    NEONATAL("uy2gU8kT1jF", "CBMNC - Neonatal Program");

    companion object {
        fun fromUid(uid: String): ProgramType = when (uid) {
            EPI.uid -> EPI
            WOMAN.uid -> WOMAN
            NEONATAL.uid -> NEONATAL
            else -> EPI // Default to EPI
        }
        fun fromName(name: String): ProgramType = when (name.lowercase()) {
            "epi" -> EPI
            "woman" -> WOMAN
            "neonatal" -> NEONATAL
            else -> EPI
        }
    }

    fun getMetadataIcon(): String = when (this) {
        EPI -> "dhis2_syringe_outline"
        WOMAN -> "dhis2_woman_positive"
        NEONATAL -> "dhis2_baby_male_0609m_positive"
    }

    fun getMetadataColor(): Color = when (this) {
        EPI -> SurfaceColor.Primary
        WOMAN -> Color(0xFFE12F58)
        NEONATAL -> Color(0xFFEF6C00)
    }
}

// MetadataIconData for Avatar
data class MetadataIconData(
    val imageCardData: ImageCardData.IconCardData,
    val color: Color
)
