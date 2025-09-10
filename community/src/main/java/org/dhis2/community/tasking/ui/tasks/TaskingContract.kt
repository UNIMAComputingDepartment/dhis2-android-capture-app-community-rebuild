package org.dhis2.community.tasking.ui.tasks

import org.dhis2.community.tasking.models.Task

interface TaskingContract {
    interface View{
        fun showLoading()
        fun showTasks(theTasks: List<Task>)
        fun showEmpty()
        fun showError(message : String)
    }

    interface Presenter{
        fun attach(view: View)
        fun detach()
        fun loadTasks(orgUnit: String? = null)
        fun onTaskClick(task:Task)
        fun onRefresh(orgUnit: String? = null)
    }
}