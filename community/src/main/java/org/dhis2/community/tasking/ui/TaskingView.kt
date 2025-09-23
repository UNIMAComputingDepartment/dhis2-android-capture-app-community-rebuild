package org.dhis2.community.tasking.ui

interface TaskingView {
    fun showTasks(tasks: List<TaskingUiModel>) // For presenter to update tasks in view
    fun clearFilters()
    fun openOrgUnitTreeSelector()
}
