package org.dhis2.community.tasking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.community.tasking.ui.TaskItemDefaults.chipIconSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.chipTextSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.detailLabelTextSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.detailValueTextSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.dueDateTextSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.programNameTextSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.showMoreIconSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.showMoreTextSize
import org.dhis2.community.tasking.ui.TaskItemDefaults.taskNameTextSize
import org.hisp.dhis.mobile.ui.designsystem.component.Avatar
import org.hisp.dhis.mobile.ui.designsystem.component.AvatarStyleData
import org.hisp.dhis.mobile.ui.designsystem.component.MetadataAvatarSize
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2TextStyle
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import org.hisp.dhis.mobile.ui.designsystem.theme.dropShadow
import org.hisp.dhis.mobile.ui.designsystem.theme.getTextStyle
import java.text.SimpleDateFormat
import java.util.Locale

object TaskItemDefaults {
    val taskNameTextSize = 15.sp
    val programNameTextSize = 13.sp
    val dueDateTextSize = 11.sp
    val chipTextSize = 12.sp
    val chipIconSize = 12.dp
    val showMoreTextSize = 12.sp
    val showMoreIconSize = 12.dp
    val detailLabelTextSize = 12.sp
    val detailValueTextSize = 12.sp
}

@Composable
fun TaskItem(
    task: TaskingUiModel,
    onTaskClick: (TaskingUiModel) -> Unit,
    showProgramName: Boolean = true,
) {
    // State management
    var expanded by remember { mutableStateOf(false) }
    val progressFraction = task.progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        label = "progress"
    )

    // Date formatting
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val dueDateText = task.dueDate?.let { dateFormatter.format(it) }

    // Determine status
    val isOverdue = task.status.label.contains("overdue", ignoreCase = true)
    val isCompleted = task.status.label.contains("completed", ignoreCase = true)
    val isDefaulted = task.status.label.contains("default", ignoreCase = true)

    // Main container
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .shadow(elevation = 16.dp, ambientColor = SurfaceColor.ContainerHighest,
                spotColor = SurfaceColor.ContainerHighest, shape = RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = buildString {
                    append("${task.taskName}, ")
                    if (showProgramName) append("${task.displayProgramName}, ")
                    append("progress ${(animatedProgress * 100).toInt()}%, ")
                    append("status ${task.status.label}, ")
                    append("priority ${task.priority.label}")
                    dueDateText?.let { append(", due $it") }
                }
            }
            .clickable(
                onClick = { onTaskClick(task) },
                role = Role.Button
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 6.dp,
            focusedElevation = 6.dp,
            hoveredElevation = 6.dp,
            draggedElevation = 8.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.White
        )
    ) {
        Column {
            // Progress indicator at the top
            if (progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = when {
                        isCompleted -> TaskingStatus.COMPLETED.color
                        isOverdue -> TaskingStatus.OVERDUE.color
                        isDefaulted -> TaskingStatus.DEFAULTED.color
                        else -> TaskingStatus.OPEN.color
                    },
                    trackColor = TaskingStatus.OPEN.color.copy(alpha = 0.3f),
                )
            }

            // Main content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Avatar(
                    style = AvatarStyleData.Metadata(
                        imageCardData = task.metadataIconData.imageCardData,
                        avatarSize = MetadataAvatarSize.S(),
                        tintColor = task.metadataIconData.color
                    )
                )

                // Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row with title and due date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val person = task.teiPrimary.takeIf { it.isNotBlank() }
                            val taskName = task.taskName.ifBlank { "Unnamed task" }

                            val annotated = buildAnnotatedString {
                                person?.let {
                                    withStyle(style = getTextStyle(DHIS2TextStyle.TITLE_LARGE).toSpanStyle().copy(
                                        fontSize = taskNameTextSize,
                                        color = TextColor.OnPrimaryContainer
                                    )) {
                                        append(it)
                                        append(" • ")
                                    }
                                }
                                withStyle(style = getTextStyle(DHIS2TextStyle.TITLE_LARGE).toSpanStyle().copy(
                                    fontSize = taskNameTextSize,
                                    color = TextColor.OnPrimaryContainer
                                )) {
                                    append(taskName)
                                }
                            }

                            Text(
                                text = annotated,
                                maxLines = 3,
                                lineHeight = 20.sp,
                                overflow = TextOverflow.Ellipsis,
                                style = getTextStyle(DHIS2TextStyle.TITLE_LARGE).copy(
                                    fontSize = taskNameTextSize,
                                    color = TextColor.OnPrimaryContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (showProgramName && task.displayProgramName.isNotEmpty()) {
                                Text(
                                    text = task.displayProgramName,
                                    style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM).copy(
                                        fontSize = programNameTextSize,
                                        color = TextColor.OnSurface
                                    )
                                )
                            }
                        }

                        // Due date badge
                        dueDateText?.let {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Due: $it",
                                        style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM).copy(
                                            fontSize = dueDateTextSize,
                                            color = TextColor.OnSurfaceLight
                                        )
                                    )
                            }

                        }
                    }

                    // Status, Priority, and Show More chips row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Priority chip
                        TaskChip(
                            label = task.priority.label,
                            icon = Icons.Default.Flag,
                            color = task.priority.color,
                            textSize = chipTextSize,
                            iconSize = chipIconSize
                        )

                        // Status chip
                        TaskChip(
                            label = task.status.label,
                            icon = when {
                                isCompleted -> Icons.Default.CheckCircle
                                isOverdue -> Icons.Default.Warning
                                isDefaulted -> Icons.Default.Stop
                                else -> Icons.Default.Schedule
                            },
                            color = task.status.color,
                            textSize = chipTextSize,
                            iconSize = chipIconSize
                        )

                        // Spacer pushes Show More to the end
                        Spacer(modifier = Modifier.weight(1f))

                        // Show More button (right-aligned)
                        if (hasExpandableContent(task)) {
                            TextButton(
                                onClick = { expanded = !expanded },
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (expanded) "Show less" else "Show more",
                                        style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM).copy(
                                            fontSize = showMoreTextSize,
                                            color = TextColor.OnSurfaceLight
                                        )
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(showMoreIconSize),
                                        tint = TextColor.OnSurfaceLight
                                    )
                                }
                            }
                        }
                    }

                    // Expandable content
                    AnimatedVisibility(
                        visible = expanded && hasExpandableContent(task),
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HorizontalDivider(
                                Modifier.padding(), 0.5.dp,
                                TextColor.OnSurface.copy(alpha = 0.2f)
                            )

                            task.teiPrimary.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Name", value = it, labelTextSize = detailLabelTextSize, valueTextSize = detailValueTextSize)
                            }
                            task.teiSecondary.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Village", value = it, labelTextSize = detailLabelTextSize, valueTextSize = detailValueTextSize)
                            }
                            task.teiTertiary.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Date of Birth", value = it, labelTextSize = detailLabelTextSize, valueTextSize = detailValueTextSize)
                            }

                            // Progress details
                            if (progressFraction > 0f) {
                                DetailRow(
                                    label = "Progress",
                                    value = "${(progressFraction * 100).toInt()}% complete",
                                    labelTextSize = detailLabelTextSize,
                                    valueTextSize = detailValueTextSize
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    textSize: TextUnit = 12.sp,
    iconSize: Dp = 14.dp
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = color
            )
            Text(
                text = label,
                style = getTextStyle(DHIS2TextStyle.LABEL_LARGE).copy(fontSize = textSize, color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    labelTextSize: TextUnit = 13.sp,
    valueTextSize: TextUnit = 13.sp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label",
            style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM).copy(fontSize = labelTextSize, color = TextColor.OnSurfaceLight),
        )
        Text(
            text = value,
            style = getTextStyle(DHIS2TextStyle.BODY_MEDIUM).copy(fontSize = valueTextSize, color = TextColor.OnSurface),
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun hasExpandableContent(task: TaskingUiModel): Boolean {
    return task.teiPrimary.isNotBlank() ||
            task.teiSecondary.isNotBlank() ||
            task.teiTertiary.isNotBlank()
}