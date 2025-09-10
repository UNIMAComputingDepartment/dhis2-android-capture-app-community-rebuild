package org.dhis2.community.tasking.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.dhis2.community.tasking.engine.CreationEvaluator
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class TaskingFragment : Fragment(), TaskingView {
    @Inject lateinit var repository: TaskingRepository
    @Inject lateinit var d2: D2

    private lateinit var presenter: TaskingPresenter
    private var tasks: List<TaskingUiModel> = emptyList()
    private val filterRepository = TaskFilterRepository()
    private val filterManager = FilterManager.getInstance()
    private val viewModel: TaskingViewModel by viewModels {
        TaskingViewModelFactory(repository, d2)
    }
    private val filterState = org.dhis2.community.tasking.filters.TaskFilterState()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("TaskingFragment onCreate called")
        presenter = TaskingPresenter(filterRepository, filterManager)
        Timber.d("TaskingPresenter initialized in Fragment")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("TaskingFragment onCreateView called")
        // Set dummy tasks in the viewModel's filteredTasks StateFlow
        (viewModel.filteredTasks as kotlinx.coroutines.flow.MutableStateFlow<List<TaskingUiModel>>).value = tasks
        Timber.d("TaskingFragment ComposeView set with ${tasks.size} tasks")
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TaskingUi(
                    tasks = viewModel.filteredTasks.collectAsState().value,
                    onTaskClick = { Timber.d("Task clicked: $it") },
                    viewModel = viewModel,
                    filterState = filterState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("TaskingFragment onResume called")
        presenter.onResume()
    }

    override fun showTasks(tasks: List<TaskingUiModel>) {
        Timber.d("TaskingFragment showTasks called with ${tasks.size} tasks")
        this.tasks = tasks
    }

    override fun clearFilters() {
        Timber.d("TaskingFragment clearFilters called")
        filterRepository.clearFilters()
    }
}
