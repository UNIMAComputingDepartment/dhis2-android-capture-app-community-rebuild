package org.dhis2.community.tasking.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.dhis2.community.tasking.models.Task


@SuppressLint("RememberReturnType")
@Composable
fun TaskListPage(
    //d2 : D2,
    tasks : List<Task>,
    teiTypeUid: String = "",
    programUid: String = "",
    orgUnitUid: String = "",
    onTaskClick: (Task) -> Unit
) {

    /*val tasks = remember (teiTypeUid, programUid, orgUnitUid) {
        TaskingRepository(d2).getAllTasks(
            teiTypeUid,
            programUid,
            orgUnitUid)
    }*/

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tasks) { task ->
            TaskCard(task, onTaskClick)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TaskListPreview() {
    val sampleTasks = listOf(
        Task(
            name = "HIV Follow-up",
            description = "Follow up patient for HIV treatment",
            sourceProgramUid = "prog1",
            sourceProgramName = "HIV Program",
            teiUid = "tei001",
            teiPrimary = "John Doe",
            teiSecondary = "Clinic A",
            teiTertiary = "25 yrs",
            dueDate = "2025-09-05",
            priority = "High Priority",
            status = "OVERDUE",
            sourceEnrollmentUid = "",
        ),
        Task(
            name = "TB Screening",
            description = "Screen patient for TB symptoms",
            sourceProgramUid = "prog2",
            sourceProgramName = "TB Program",
            teiUid = "tei002",
            teiPrimary = "Jane Smith",
            teiSecondary = "Clinic B",
            teiTertiary = "30 yrs",
            dueDate = "2025-09-10",
            priority = "Low Priority",
            status = "COMPLETED",
            sourceEnrollmentUid = "",
        ),
        Task(
            name = "Nutrition Check",
            description = "Assess nutritional status of child Follow up in 6 months",
            sourceProgramUid = "prog3",
            sourceProgramName = "Nutrition Program",
            teiUid = "tei003",
            teiPrimary = "Baby Joe",
            teiSecondary = "Clinic C",
            teiTertiary = "2 yrs",
            dueDate = "2025-09-12",
            priority = "Medium Priority",
            status = "OPEN",
            sourceEnrollmentUid = ""
        )
    )

    TaskListPage(sampleTasks) { clickedTask ->
        // For preview, just print the clicked task name
        println("Clicked task: ${clickedTask.name}")
    }
}