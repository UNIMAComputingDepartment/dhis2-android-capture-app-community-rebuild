package org.dhis2.usescases.tasks

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TasksViewModel : ViewModel() {
    private val _completionRate = MutableStateFlow(0.35f)
    val completionRate: StateFlow<Float> = _completionRate

    fun init() {
        // Will be implemented with real data later
    }

    fun updateTasks() {
        // Will be implemented to refresh tasks after sync
    }
}
