package org.dhis2.community.tasking.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.community.tasking.models.TaskDownloadState
import org.dhis2.community.tasking.models.TaskingUiModel
import org.dhis2.community.tasking.models.TaskingPriority
import org.dhis2.community.tasking.models.TaskingStatus
import org.dhis2.community.tasking.models.dummyTasks
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.Avatar
import org.hisp.dhis.mobile.ui.designsystem.component.AvatarStyleData
import org.hisp.dhis.mobile.ui.designsystem.component.ExpandableItemColumn
import org.hisp.dhis.mobile.ui.designsystem.component.ImageCardData
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.MetadataAvatarSize
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicator
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicatorType
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.text.SimpleDateFormat
import java.util.Locale

@Preview(showBackground = true)
@Composable
fun TaskingUi(
    tasks: List<TaskingUiModel> = dummyTasks,
    onTaskClick: (TaskingUiModel) -> Unit = {}
) {
    val completedTasks = tasks.count { it.status == TaskingStatus.COMPLETED }
    val totalTasks = tasks.size
    val completionRate = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // Task Progress Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Task Progress",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextColor.OnSurface
            )
            Text(
                text = "$completedTasks of $totalTasks completed",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = TextColor.OnSurface
            )
        }

        ProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(12.dp),
            type = ProgressIndicatorType.LINEAR,
            progress = completionRate
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExpandableItemColumn(
            modifier = Modifier.fillMaxSize(),
            itemList = tasks
        ) { task, verticalPadding, onSizeChanged ->
            TaskItem(
                modifier = Modifier,
                task = task,
                verticalPadding = verticalPadding,
                onSizeChanged = onSizeChanged,
                onTaskClick = onTaskClick
            )
        }
    }
}

@Composable
fun TaskCardDescription(subtitle: String): ListCardDescriptionModel {
    return ListCardDescriptionModel(text = subtitle)
}

@Composable
private fun PriorityIcon(priority: TaskingPriority) {
    Icon(
        imageVector = Icons.Outlined.Flag,
        contentDescription = "Priority",
        tint = priority.color,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun StatusIcon(status: TaskingStatus) {
    Icon(
        imageVector = Icons.Outlined.Event,
        contentDescription = "Status",
        tint = status.color,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
fun TaskAvatar(task: TaskingUiModel, avatarSize: MetadataAvatarSize) {
    val context = LocalContext.current
    val imageCardData = task.metadataIconData.imageCardData
    val iconResName = if (imageCardData is ImageCardData.IconCardData) imageCardData.iconRes else null
    val iconResId = iconResName?.let {
        context.resources.getIdentifier(it, "drawable", context.packageName)
    } ?: 0
    val avatarImageCardData = if (imageCardData is ImageCardData.IconCardData) {
        ImageCardData.IconCardData(
            uid = imageCardData.uid,
            label = imageCardData.label,
            iconRes = iconResId.toString(),
            iconTint = imageCardData.iconTint
        )
    } else imageCardData
    Avatar(
        style = AvatarStyleData.Metadata(
            imageCardData = avatarImageCardData,
            avatarSize = avatarSize,
            tintColor = task.metadataIconData.color,
        ),
    )
}

@Composable
private fun SyncingAdditionalInfoItem(task: TaskingUiModel): AdditionalInfoItem {
    return when (task.downloadState) {
        TaskDownloadState.DOWNLOADING -> AdditionalInfoItem(
            icon = { ProgressIndicator(type = ProgressIndicatorType.CIRCULAR_SMALL) },
            value = "Syncing...",
            color = TextColor.OnSurfaceLight,
            isConstantItem = true
        )
        TaskDownloadState.DOWNLOADED -> AdditionalInfoItem(
            icon = { Icon(Icons.Outlined.Celebration, contentDescription = "downloaded", tint = TextColor.OnSurfaceLight) },
            value = "Downloaded",
            color = TextColor.OnSurfaceLight,
            isConstantItem = true
        )
        TaskDownloadState.ERROR -> AdditionalInfoItem(
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = "error", tint = TextColor.OnErrorContainer) },
            value = "Error",
            color = TextColor.OnErrorContainer,
            isConstantItem = true
        )
        TaskDownloadState.NONE -> AdditionalInfoItem(
            value = "",
            color = TextColor.OnSurfaceLight,
            isConstantItem = true
        )
    }
}

@Composable
fun TaskItem(
    modifier: Modifier,
    task: TaskingUiModel,
    verticalPadding: Dp,
    onSizeChanged: (IntSize) -> Unit,
    onTaskClick: (TaskingUiModel) -> Unit
) {
    Log.d("TaskDebug", "Rendering task: ${task.title}, Priority: ${task.priority.label}, Status: ${task.status.label}")
    val title = ListCardTitleModel(
        text = task.title,
        color = TextColor.OnPrimaryContainer
    )
    val dueDateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(task.dueDate)

    ListCard(
        modifier = modifier,
        listCardState = rememberListCardState(
            title = title,
            description = ListCardDescriptionModel(text = task.subtitle),
            lastUpdated = "Due: $dueDateFormatted",
            loading = task.downloadState == TaskDownloadState.DOWNLOADING,
            additionalInfoColumnState = rememberAdditionalInfoColumnState(
                additionalInfoList = buildList {
                    // Priority
                    add(
                        AdditionalInfoItem(
                            icon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = task.priority.infoColor.color) },
                            value = task.priority.label,
                            color = task.priority.infoColor.color,
                            truncate = false,
                            isConstantItem = true
                        )
                    )
                    // Status
                    add(
                        AdditionalInfoItem(
                            icon = { Icon(Icons.Outlined.Event, contentDescription = null, tint = task.status.infoColor.color) },
                            value = task.status.label,
                            color = task.status.infoColor.color,
                            truncate = false,
                            isConstantItem = true
                        )
                    )
                    // Client info
                    add(
                        AdditionalInfoItem(
                            key = "Client",
                            value = task.clientName,
                            color = TextColor.OnSurface, // value color
                            truncate = false,
                            isConstantItem = false
                        )
                    )
                    // Location info
                    add(
                        AdditionalInfoItem(
                            key = "Location",
                            value = task.location,
                            color = TextColor.OnSurface, // value color
                            truncate = false,
                            isConstantItem = false
                        )
                    )
                    // Program Stage
                    add(
                        AdditionalInfoItem(
                            key = "Program Stage",
                            value = task.programStage,
                            color = TextColor.OnSurface,
                            truncate = false,
                            isConstantItem = false
                        )
                    )
                },
                expandLabelText = "Show details",
                shrinkLabelText = "Hide details",
                minItemsToShow = 2,
                syncProgressItem = SyncingAdditionalInfoItem(task),
            ),
            expandable = true,
            itemVerticalPadding = verticalPadding
        ),
        listAvatar = {
            TaskAvatar(task = task, avatarSize = MetadataAvatarSize.S())
        },
        actionButton = {},
        onCardClick = { onTaskClick(task) },
        onSizeChanged = onSizeChanged
    )
}