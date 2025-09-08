package org.dhis2.community.tasking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.dhis2.community.tasking.repositories.TaskingRepository
import javax.inject.Inject

class TaskingViewModelFactory(
    private val repository: TaskingRepository,
    private val d2: org.hisp.dhis.android.core.D2
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskingViewModel(repository, d2) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
