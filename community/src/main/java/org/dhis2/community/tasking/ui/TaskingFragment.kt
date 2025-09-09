package org.dhis2.community.tasking.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.dhis2.community.tasking.engine.CreationEvaluator
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import javax.inject.Inject


class TaskingFragment : Fragment(), TaskingView {
    private lateinit var presenter: TaskingPresenter
    private var tasks: List<TaskingUiModel> = emptyList()
    private val filterRepository = TaskFilterRepository()
    private val filterManager = FilterManager.getInstance()
    // Stubbed TaskingViewModel (replace with real repository/D2 if available)
    private val viewModel = object : TaskingViewModelContract {
        override val filteredTasks = kotlinx.coroutines.flow.MutableStateFlow<List<TaskingUiModel>>(emptyList())
        override val programs = emptyList<org.hisp.dhis.mobile.ui.designsystem.component.CheckBoxData>()
        override val priorities = emptyList<org.hisp.dhis.mobile.ui.designsystem.component.CheckBoxData>()
        override val statuses = emptyList<org.hisp.dhis.mobile.ui.designsystem.component.CheckBoxData>()
        override val orgUnits = emptyList<org.hisp.dhis.mobile.ui.designsystem.component.OrgTreeItem>()
        override val allTasksForProgress = emptyList<TaskingUiModel>()
        override fun onFilterChanged() {}
    }
    private val filterState = org.dhis2.community.tasking.filters.TaskFilterState()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter = TaskingPresenter(filterRepository, filterManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val tasks = getDummyTasks()
        // Set dummy tasks in the viewModel's filteredTasks StateFlow
        (viewModel.filteredTasks as kotlinx.coroutines.flow.MutableStateFlow<List<TaskingUiModel>>).value = tasks
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TaskingUi(
                    tasks = tasks, // Pass dummy tasks directly
                    onTaskClick = { /* handle click */ },
                    viewModel = viewModel,
                    filterState = filterState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun showTasks(tasks: List<TaskingUiModel>) {
        this.tasks = tasks
    }

    override fun clearFilters() {
        filterRepository.clearFilters()
    }
}
