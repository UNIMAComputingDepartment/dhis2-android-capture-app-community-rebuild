package org.dhis2.community.tasking.ui.tasks

//noinspection UsingMaterialAndMaterial3Libraries
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.dhis2.commons.resources.ColorUtils
import org.dhis2.commons.resources.ResourceManager
import org.dhis2.community.R
import org.dhis2.community.relationships.ui.Dhis2CmtTheme
import org.dhis2.community.tasking.models.Task
import org.hisp.dhis.lib.expression.syntax.ExpressionGrammar.item


@OptIn(ExperimentalStdlibApi::class)
@Composable
fun TaskCard(
    task: Task,
    onTaskClick: (Task) -> Unit
) {
    Dhis2CmtTheme {
        TaskCardContents(
            task = task,
            onTaskClick = onTaskClick
        )
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
fun TaskCardContents(
    task: Task,
    //repo: TaskingRepository = TaskingRepository,
    onTaskClick: (Task) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                onTaskClick(task)
            }
            .animateContentSize(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                //verticalAlignment = Alignment.CenterVertically
            ) {

                Row() {
                    // Program Icon placeholder (replace with actual program icons)
                    TaskIconBox(item = task)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.width(230.dp)

                    ) {
                        Text(
                            text = task.sourceProgramName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            // maxLines = Int.MAX_VALUE
                        )
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PriorityChip(priority = task.priority)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Client: ${task.teiPrimary}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Location: ${task.teiSecondary}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Task Due: ${task.dueDate}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (expanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Description: ${task.description}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Extra: ${task.teiTertiary}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Expand/Collapse indicator
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (expanded) "▲ Show less" else "▼ Show more",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0288D1),
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = when (task.status.uppercase()) {
                        "OPEN" -> "Upcoming"
                        "COMPLETED" -> "Done"
                        "OVERDUE" -> "Overdue"
                        else -> task.status
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (task.status.uppercase()) {
                        "OVERDUE" -> Color.Red
                        "OPEN" -> Color(0xFFF57C00) // blue for upcoming
                        "COMPLETED" -> Color(0xFF388E3C)
                        else -> Color.DarkGray
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview
@Composable
fun PriorityChip(priority: String = "High") {
    val (bgColor, textColor) = when (priority) {
        "High Priority" -> Pair(Color(0xFFFFCDD2), Color.Red)
        "Medium Priority" -> Pair(Color(0xFFFFF9C4), Color(0xFFF57C00))
        "Low Priority" -> Pair(Color(0xFFC8E6C9), Color(0xFF388E3C))
        else -> Pair(Color.LightGray, Color.Black)
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            priority.replaceFirstChar { it.uppercase() },
            color = textColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

//@Preview(showBackground = true)
@Composable
fun TaskIconBox(
    icon: String? = Icons.Default.Assignment.toString(), // default icon
    contentDescription: String = "Task",
    context : Context = LocalContext.current,
    colorUtils: ColorUtils = ColorUtils(),
    res: ResourceManager = ResourceManager(context,colorUtils),
    item: Task
) {
    val tieTypeIcon = res.getObjectStyleDrawableResource(item.iconNane, R.drawable.ic_alert_outline)

    Box(

    modifier = Modifier
            .size(40.dp)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(8.dp)
            )
            .padding(5.dp), // square with rounded edges
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(tieTypeIcon),
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}

