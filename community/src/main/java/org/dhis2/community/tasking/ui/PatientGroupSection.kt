package org.dhis2.community.tasking.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor
import timber.log.Timber

@Composable
fun PatientGroupSection(
    modifier: Modifier = Modifier,
    group: PatientTaskGroup,
    onTaskClick: (TaskingUiModel) -> Unit,
    isExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    Timber.d("Rendering group: ${group.patientName}, Expanded: $isExpanded, Tasks: ${group.tasks.size}")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 4.dp,
                ambientColor = SurfaceColor.ContainerHighest,
                spotColor = SurfaceColor.ContainerHighest,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 100))
        ) {
            // Header - Patient section with expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .clickable {
                        Timber.d("Header clicked for ${group.patientName}, current state: $isExpanded, toggling to ${!isExpanded}")
                        onExpandedChange(!isExpanded)
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Patient name and task count
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = group.patientName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextColor.OnPrimaryContainer
                    )
                    Text(
                        text = "${group.taskCount} task${if (group.taskCount != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = TextColor.OnSurfaceLight
                    )
                }

                // Expand/Collapse icon - points down when closed, up when open
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse patient tasks" else "Expand patient tasks",
                    modifier = Modifier
                        .rotate(if (isExpanded) 180f else 0f)
                        .padding(8.dp),
                    tint = TextColor.OnPrimaryContainer
                )
            }

            // Expanded tasks list - ONLY render when expanded
            if (isExpanded) {
                Timber.d("Showing tasks for ${group.patientName} - ${group.tasks.size} tasks")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    group.tasks.forEach { task ->
                        TaskItem(
                            task = task,
                            onTaskClick = onTaskClick,
                            showPatientName = false
                        )
                    }
                }
            }
        }
    }
}
