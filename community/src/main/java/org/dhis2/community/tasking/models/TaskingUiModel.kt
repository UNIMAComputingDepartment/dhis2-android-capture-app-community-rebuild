package org.dhis2.community.tasking.models

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.dhis2.ui.MetadataIconData
import org.hisp.dhis.mobile.ui.designsystem.component.ImageCardData
import java.util.Date
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItemColor

enum class TaskingPriority(
    val label: String,
    val color: Color,
    val infoColor: AdditionalInfoItemColor
) {
    HIGH("High Priority", TextColor.OnError, AdditionalInfoItemColor.ERROR),
    MEDIUM("Medium Priority", TextColor.OnWarning, AdditionalInfoItemColor.WARNING),
    LOW("Low Priority", TextColor.OnPrimary, AdditionalInfoItemColor.DEFAULT_VALUE);

    companion object {
        fun fromLabel(label: String): TaskingPriority = entries.find { it.label == label } ?: LOW
    }
}

enum class TaskingStatus(
    val label: String,
    val color: Color,
    val infoColor: AdditionalInfoItemColor
) {
    UPCOMING("Upcoming", TextColor.OnPrimary, AdditionalInfoItemColor.DEFAULT_KEY),
    DUE_SOON("Due Soon", TextColor.OnWarning, AdditionalInfoItemColor.WARNING),
    OVERDUE("Overdue", TextColor.OnError, AdditionalInfoItemColor.ERROR),
    COMPLETED("Completed", SurfaceColor.CustomGreen, AdditionalInfoItemColor.SUCCESS);

    companion object {
        fun fromLabel(label: String): TaskingStatus = entries.find { it.label == label } ?: UPCOMING
    }
}

enum class TaskDownloadState {
    DOWNLOADING, DOWNLOADED, ERROR, NONE
}

data class TaskingUiModel(
    val uid: String,
    val title: String,
    val subtitle: String,
    val priority: TaskingPriority,
    val status: TaskingStatus,
    val clientName: String,
    val location: String,
    val dueDate: Date,
    val programStage: String,
    val program: ProgramType, // EPI, WOMAN, NEONATAL
    val metadataIconData: MetadataIconData,
    val downloadState: TaskDownloadState = TaskDownloadState.NONE,
    val downloadActive: Boolean = false
)

enum class ProgramType(val label: String) {
    EPI("EPI"),
    WOMAN("Woman"),
    NEONATAL("Neonatal");

    fun getMetadataIcon(): String = when (this) {
        EPI -> "dhis2_syringe_outline"
        WOMAN -> "dhis2_woman_positive"
        NEONATAL -> "dhis2_baby_male_0609m_positive"
    }

    fun getStages(): List<String> = when (this) {
        EPI -> listOf(
            "Immunization for children under 5 years",
            "Nutrition for children under 5 years",
            "Growth monitoring",
            "Immunization for children above 5 Years"
        )
        WOMAN -> listOf(
            "Pregnancy Period",
            "Delivery",
            "After Birth",
            "Death"
        )
        NEONATAL -> listOf(
            "Post Delivery Care",
            "Death"
        )
    }
}

val dummyTasks = listOf(
    // EPI tasks
    TaskingUiModel(
        uid = "0",
        title = "COVID-19 Vaccination",
        subtitle = "COVID-19 Immunization Program",
        priority = TaskingPriority.HIGH,
        status = TaskingStatus.COMPLETED,
        clientName = "James Phiri",
        location = "Lilongwe District Hospital",
        dueDate = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 13) }.time,
        programStage = ProgramType.EPI.getStages()[0],
        program = ProgramType.EPI,
        metadataIconData = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = "0",
                label = "COVID-19 Vaccination",
                iconRes = ProgramType.EPI.getMetadataIcon(), // String resource name
                iconTint = TaskingStatus.COMPLETED.color
            ),
            color = TaskingStatus.COMPLETED.color
        )
    ),
    TaskingUiModel(
        uid = "1",
        title = "BCG Immunization",
        subtitle = "EPI - Expanded Programme on Immunization",
        priority = TaskingPriority.MEDIUM,
        status = TaskingStatus.OVERDUE,
        clientName = "Chimwemwe Banda",
        location = "Chitala Village, Salima",
        dueDate = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 14) }.time,
        programStage = ProgramType.EPI.getStages()[1],
        program = ProgramType.EPI,
        metadataIconData = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = "1",
                label = "BCG Immunization",
                iconRes = ProgramType.EPI.getMetadataIcon(), // String resource name
                iconTint = TaskingStatus.OVERDUE.color
            ),
            color = TaskingStatus.OVERDUE.color
        )
    ),
    // Woman program tasks
    TaskingUiModel(
        uid = "2",
        title = "ANC Visit #1",
        subtitle = "CBMNC - Woman Program",
        priority = TaskingPriority.LOW,
        status = TaskingStatus.DUE_SOON,
        clientName = "Mphatso Phiri",
        location = "Bangula Village, Nsanje",
        dueDate = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 15) }.time,
        programStage = ProgramType.WOMAN.getStages()[0],
        program = ProgramType.WOMAN,
        metadataIconData = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = "2",
                label = "ANC Visit #1",
                iconRes = ProgramType.WOMAN.getMetadataIcon(), // String resource name
                iconTint = TaskingStatus.DUE_SOON.color
            ),
            color = TaskingStatus.DUE_SOON.color
        )
    ),
    TaskingUiModel(
        uid = "3",
        title = "Delivery",
        subtitle = "CBMNC - Woman Program",
        priority = TaskingPriority.HIGH,
        status = TaskingStatus.COMPLETED,
        clientName = "Thandiwe Banda",
        location = "Mzimba North",
        dueDate = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 16) }.time,
        programStage = ProgramType.WOMAN.getStages()[1],
        program = ProgramType.WOMAN,
        metadataIconData = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = "3",
                label = "Delivery",
                iconRes = ProgramType.WOMAN.getMetadataIcon(), // String resource name
                iconTint = TaskingStatus.UPCOMING.color
            ),
            color = TaskingStatus.UPCOMING.color
        )
    ),
    // Neonatal program tasks
    TaskingUiModel(
        uid = "4",
        title = "Post Delivery Care",
        subtitle = "CBMNC - Neonatal Program",
        priority = TaskingPriority.HIGH,
        status = TaskingStatus.UPCOMING,
        clientName = "Esmie Jere",
        location = "Kalemba Zone, Machinga",
        dueDate = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 18) }.time,
        programStage = ProgramType.NEONATAL.getStages()[0],
        program = ProgramType.NEONATAL,
        metadataIconData = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = "4",
                label = "Post Delivery Care",
                iconRes = ProgramType.NEONATAL.getMetadataIcon(), // String resource name
                iconTint = TaskingStatus.UPCOMING.color
            ),
            color = TaskingStatus.UPCOMING.color
        )
    ),
    TaskingUiModel(
        uid = "5",
        title = "Neonatal Death",
        subtitle = "CBMNC - Neonatal Program",
        priority = TaskingPriority.LOW,
        status = TaskingStatus.OVERDUE,
        clientName = "Gift Mwale",
        location = "Kasungu Rural",
        dueDate = Calendar.getInstance().apply { set(2025, Calendar.AUGUST, 19) }.time,
        programStage = ProgramType.NEONATAL.getStages()[1],
        program = ProgramType.NEONATAL,
        metadataIconData = MetadataIconData(
            imageCardData = ImageCardData.IconCardData(
                uid = "5",
                label = "Neonatal Death",
                iconRes = ProgramType.NEONATAL.getMetadataIcon(), // String resource name
                iconTint = TaskingStatus.OVERDUE.color
            ),
            color = TaskingStatus.OVERDUE.color
        )
    )
)
