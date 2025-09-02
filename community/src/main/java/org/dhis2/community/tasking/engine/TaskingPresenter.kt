package org.dhis2.community.tasking.engine

import org.dhis2.community.tasking.ui.TaskingView
import org.dhis2.community.tasking.models.TaskingUiModel
import org.dhis2.community.tasking.models.dummyTasks

class TaskingPresenter(private val view: TaskingView) {
    val tasks: List<TaskingUiModel> = dummyTasks
    fun onResume() {
    }
}
