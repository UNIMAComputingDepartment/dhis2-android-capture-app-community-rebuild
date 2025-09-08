package org.dhis2.community.tasking.filters

data class TaskFilter(
    val programFilters: Set<String> = emptySet(),
    val orgUnitFilters: Set<String> = emptySet(),
    val priorityFilters: Set<String> = emptySet(),
    val statusFilters: Set<String> = emptySet(),
    val dueDateRange: org.dhis2.community.tasking.filters.models.DateRangeFilter? = null,
    val customDateRange: org.dhis2.community.tasking.filters.models.CustomDateRange? = null
) {
    fun isEmpty(): Boolean = programFilters.isEmpty() && orgUnitFilters.isEmpty() &&
        priorityFilters.isEmpty() && statusFilters.isEmpty() && dueDateRange == null && customDateRange == null
}
