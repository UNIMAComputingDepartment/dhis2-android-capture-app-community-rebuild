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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.hisp.dhis.mobile.ui.designsystem.component.Avatar
import org.hisp.dhis.mobile.ui.designsystem.component.AvatarStyleData
import org.hisp.dhis.mobile.ui.designsystem.component.MetadataAvatarSize
import java.text.SimpleDateFormat
import java.util.Locale

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
            .padding(horizontal = 6.dp, vertical = 6.dp)
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
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
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
                                        isCompleted -> Color(0xFF4CAF50)
                                        isOverdue -> Color(0xFFFF5722)
                                        isDefaulted -> Color(0xFF9E9E9E)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
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
                                    append(it)
                                    append(" • ")
                                }
                                withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                    append(taskName)
                                }
                            }

                            Text(
                                text = annotated,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )


                            if (showProgramName && task.displayProgramName.isNotEmpty()) {
                                Text(
                                    text = task.displayProgramName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Due date badge
                        dueDateText?.let {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color =task.status.color.copy(alpha = 0.12f),
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = task.status.color
                                    )
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = task.status.color
                                    )
                                }
                            }
                        }
                    }

                    // Status and Priority chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Priority chip
                        TaskChip(
                            label = task.priority.label,
                            icon = Icons.Default.Star,
                            color = task.priority.color
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
                            color = task.status.color
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Expand/Collapse button
                        if (hasExpandableContent(task)) {
                            TextButton(
                                onClick = { expanded = !expanded },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (expanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
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
                                MaterialTheme.colorScheme.surfaceVariant
                            )

                            task.teiPrimary.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Name", value = it)
                            }

                            task.teiSecondary.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Village", value = it)
                            }

                            task.teiTertiary.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "DOB", value = it)
                            }

                            // Progress details
                            if (progressFraction > 0f) {
                                DetailRow(
                                    label = "Progress",
                                    value = "${(progressFraction * 100).toInt()}% complete"
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
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
    }
}

private fun hasExpandableContent(task: TaskingUiModel): Boolean {
    return task.teiPrimary.isNotBlank() ||
            task.teiSecondary.isNotBlank() ||
            task.teiTertiary.isNotBlank()
}