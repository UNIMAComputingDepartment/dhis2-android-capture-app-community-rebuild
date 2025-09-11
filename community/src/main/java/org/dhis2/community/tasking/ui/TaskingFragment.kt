
package org.dhis2.community.tasking.ui
/*
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
import timber.log.Timber
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2


class TaskingFragment : Fragment(), TaskingView {
    private lateinit var repository: TaskingRepository
    private lateinit var d2: D2
    private lateinit var presenter: TaskingPresenter
    private lateinit var filterRepository: TaskFilterRepository
    private lateinit var filterManager: FilterManager

    private var tasks: List<TaskingUiModel> = emptyList()
    private lateinit var viewModel: TaskingViewModel
    private val filterState = org.dhis2.community.tasking.filters.TaskFilterState()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("TaskingFragment onCreate called")
        // Manually obtain D2 instance (replace with your actual method)
        d2 = org.hisp.dhis.android.core.D2Manager.getD2() // or your actual D2 provider
        repository = TaskingRepository(d2)
        filterRepository = TaskFilterRepository()
        filterManager = FilterManager.getInstance()
        presenter = TaskingPresenter(filterRepository, filterManager)
        viewModel = TaskingViewModel(repository, d2)
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
                    filterState = filterState,
                    onOrgUnitFilterSelected = {
                        openOrgUnitTreeSelector()
                    }
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

    override fun openOrgUnitTreeSelector() {
        OUTreeFragment.Builder()
            .withPreselectedOrgUnits(
                FilterManager.getInstance().orgUnitFilters.map { it.uid() }.toMutableList(),
            )
            .onSelection { selectedOrgUnits ->
                presenter.setOrgUnitFilters(selectedOrgUnits)
            }
            .build()
            .show(parentFragmentManager, "OUTreeFragment")
    }

    override fun clearFilters() {
        Timber.d("TaskingFragment clearFilters called")
        filterRepository.clearFilters()
    }
}
*/
