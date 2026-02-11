package org.dhis2.community.tasking.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicator
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicatorType
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

@Composable
fun TaskProgressSection(
    completedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val completionPercentage = remember(completedCount, totalCount) {
        if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    }
    val completionPercentInt = (completionPercentage * 100).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            // Header row: Title + compact summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task Progress",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextColor.OnSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // compact numeric summary
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$completedCount of $totalCount completed ($completionPercentInt%)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextColor.OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            ProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(top = 8.dp),
                type = ProgressIndicatorType.LINEAR,
                progress = completionPercentage
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}