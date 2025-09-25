package org.dhis2.community.tasking.ui

// Imports you may need
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.dhis2.community.tasking.ui.TaskingUiModel
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.Avatar
import org.hisp.dhis.mobile.ui.designsystem.component.AvatarStyleData
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.MetadataAvatarSize
import org.hisp.dhis.mobile.ui.designsystem.component.SelectionState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TaskItem(
    task: TaskingUiModel,
    onTaskClick: (TaskingUiModel) -> Unit
) {
    // --- compute progress fraction robustly (0f..1f) ---
    val rawProgressFraction: Float = when {
        // prefer an explicit fraction on the model if you have it
        true -> (task.progressPercent / 100f).coerceIn(0f, 1f)
        // try to parse a "75%" style string
        !task.progressDisplay.isNullOrBlank() -> {
            task.progressDisplay.trim()
                .removeSuffix("%")
                .toFloatOrNull()
                ?.div(100f)
                ?: 0f
        }
        else -> 0f
    } as Float
    // animate for smooth visual transitions
    val animatedProgress by animateFloatAsState(targetValue = rawProgressFraction)

    // colors
    val progressColor = task.progressColor
    val statusColor = task.status.color
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Build the info list (we removed the textual progress item because we show progress visually)
    val infoList = listOf(
        AdditionalInfoItem(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = "Priority",
                    tint = task.priority.color,
                    modifier = Modifier.size(20.dp)
                )
            },
            value = task.priority.label,
            color = task.priority.color,
            truncate = true,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = "Status",
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            },
            value = task.status.label,
            color = statusColor,
            truncate = true,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            key = "Name",
            value = task.teiPrimary,
            color = TextColor.OnSurface,
            truncate = true,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            key = "Village",
            value = task.teiSecondary,
            color = TextColor.OnSurface,
            truncate = true,
            isConstantItem = false
        ),
        AdditionalInfoItem(
            key = "DOB",
            value = task.teiTertiary,
            color = TextColor.OnSurface,
            truncate = true,
            isConstantItem = false
        )
    )

    val additionalInfoState = rememberAdditionalInfoColumnState(
        additionalInfoList = infoList,
        syncProgressItem = AdditionalInfoItem(
            value = "",
            color = TextColor.OnSurface,
            isConstantItem = true
        ),
        expandLabelText = "Show more",
        shrinkLabelText = "Show less",
        minItemsToShow = 2,
        scrollableContent = false
    )

    val listCardState = rememberListCardState(
        title = ListCardTitleModel(
            text = task.taskName,
            color = SurfaceColor.Primary
        ),
        description = ListCardDescriptionModel(
            text = task.displayProgramName,
            color = TextColor.OnSurface
        ),
        lastUpdated = task.dueDate?.let {
            "Due: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)}"
        },
        additionalInfoColumnState = additionalInfoState,
        loading = false,
        shadow = true,
        itemVerticalPadding = 12.dp,
        selectionState = SelectionState.NONE
    )

    // --- Card with faint progress band behind it ---
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                // accessible label combining the main pieces of content
                contentDescription =
                    "${task.taskName}, ${task.displayProgramName}, progress ${ (animatedProgress * 100).toInt() } percent"
            }
    ) {
        // Progress band: a faint tinted band whose width corresponds to progress.
        // It sits behind the content (z-order: first child)
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                .height(IntrinsicSize.Min) // let the content determine height
                .align(Alignment.TopStart)
                .background(progressColor.copy(alpha = 0.10f)) // faint band
                .clip(RoundedCornerShape(12.dp))
        )

        // Actual ListCard placed on top of the band. We give it a transparent background
        // so the band subtly shows behind. We also add internal padding for spacing.
        ListCard(
            modifier = Modifier
                .background(surfaceColor.copy(alpha = 0.98f)) // keep card readable
                .clickable(
                    onClick = { onTaskClick(task) },
                    role = Role.Button
                ),
            listCardState = listCardState,
            listAvatar = {
                Avatar(
                    style = AvatarStyleData.Metadata(
                        imageCardData = task.metadataIconData.imageCardData,
                        avatarSize = MetadataAvatarSize.S(),
                        tintColor = task.metadataIconData.color
                    )
                )
            },
            onCardClick = { onTaskClick(task) },
            onSizeChanged = {}
        )
    }
}
