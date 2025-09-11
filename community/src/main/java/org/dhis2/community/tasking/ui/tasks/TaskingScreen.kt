package org.dhis2.community.tasking.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.community.tasking.models.Task
import java.util.Collections.emptyList

@Composable
fun TaskingScreen(
    presenter: TaskingContract.Presenter,
    orgUnitUid: String? = null
) {
    // Compose state observed by the UI
    var isLoading by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(listOf<Task>()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Implement MVP View interface inside Composable
    val view = remember {
        object : TaskingContract.View {
            override fun showLoading() {
                isLoading = true
                error = null
            }

            override fun showTasks(theTasks: List<Task>) {
                isLoading = false
                tasks = theTasks
                error = null
            }

            override fun showEmpty() {
                isLoading = false
                tasks = emptyList()
                error = null
            }

            override fun showError(message: String) {
                isLoading = false
                error = message
            }
        }
    }

    // Attach/detach presenter when this Composable enters/leaves composition
    DisposableEffect(Unit) {
        presenter.attach(view)
        presenter.loadTasks(orgUnitUid)
        onDispose { presenter.detach() }
    }

    // UI Rendering
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> ErrorView(error!!, onRetry = { presenter.onRefresh(orgUnitUid) })

            tasks.isEmpty() -> EmptyView(onRefresh = { presenter.onRefresh(orgUnitUid) })

            else -> TaskListPage(
                tasks = tasks,
                onTaskClick = presenter::onTaskClick,
                //onRefresh = { presenter.onRefresh(programUid) }
            )
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Something went wrong.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyView(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("No tasks yet.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefresh) { Text("Refresh") }
    }
}
