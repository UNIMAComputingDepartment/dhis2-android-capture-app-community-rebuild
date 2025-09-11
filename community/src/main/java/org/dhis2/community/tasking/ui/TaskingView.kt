package org.dhis2.community.tasking.ui

interface TaskingView {
    fun showTasks(tasks: List<TaskingUiModel>)
    fun clearFilters()
    fun openOrgUnitTreeSelector()
}
