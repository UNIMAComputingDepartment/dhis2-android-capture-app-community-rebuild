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
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicator
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicatorType
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

@Composable
fun TaskProgressSection(
    completedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val progressFraction = remember(completedCount, totalCount) {
        if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(0.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextColor.OnSurface,
                )

                Spacer(modifier = Modifier.weight(1f))

                // compact numeric summary
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$completedCount / $totalCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextColor.OnSurface,
                        fontWeight = FontWeight.Medium
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
                progress = progressFraction
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

