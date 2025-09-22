package org.dhis2.community.tasking.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.dhis2.community.tasking.engine.DefaultingEvaluator
import org.dhis2.commons.orgunitselector.OUTreeFragment


class TaskingFragment(private val onTaskClick: (Context, String, String, String) -> Unit) : Fragment(), TaskingView {
    private lateinit var repository: TaskingRepository
    private lateinit var d2: D2
    private lateinit var presenter: TaskingPresenter
    private lateinit var filterRepository: TaskFilterRepository
    private lateinit var filterManager: FilterManager
    private lateinit var defaultingEvaluator: DefaultingEvaluator

    private var tasks: List<TaskingUiModel> = emptyList()
    private lateinit var viewModel: TaskingViewModel
    private val filterState = org.dhis2.community.tasking.filters.TaskFilterState()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TaskingFragment", "TaskingFragment onCreate called")
        d2 = org.hisp.dhis.android.core.D2Manager.getD2()
        repository = TaskingRepository(d2)
        filterRepository = TaskFilterRepository()
        filterManager = FilterManager.getInstance()
        defaultingEvaluator = DefaultingEvaluator(repository, d2)
        presenter = TaskingPresenter(filterRepository, filterManager, repository, defaultingEvaluator)
        viewModel = TaskingViewModel(repository, d2)
        presenter.init(this) // Initialize presenter with this fragment as view
        Log.d("TaskingFragment", "TaskingPresenter initialized")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("TaskingFragment", "TaskingFragment onCreateView called")
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TaskingUi(
                    tasks = viewModel.filteredTasks.collectAsState().value,
                    onTaskClick = {
                        onTaskClick(
                            requireContext(),
                            it.sourceTeiUid,
                            it.sourceProgramUid,
                            it.sourceEnrollmentUid
                        )
                        Log.d("TaskingFragment", "Task clicked: $it")
                    },
                    viewModel = viewModel,
                    filterState = viewModel.filterState,
                    onOrgUnitFilterSelected = {
                        openOrgUnitTreeSelector()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("TaskingFragment", "TaskingFragment onResume called")
        presenter.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.clear()
    }

    override fun showTasks(tasks: List<TaskingUiModel>) {
        Log.d("TaskingFragment", "Showing ${tasks.size} tasks")
        viewModel.updateTasks(tasks)
    }

    override fun clearFilters() {
        Log.d("TaskingFragment", "Clearing filters")
        filterRepository.clearFilters()
    }

    override fun openOrgUnitTreeSelector() {
        OUTreeFragment.Builder()
            .withPreselectedOrgUnits(
                viewModel.filterState.currentFilter.orgUnitFilters.toList()
            )
            .onSelection { selectedOrgUnits ->
                viewModel.filterState.updateOrgUnitFilters(
                    selectedOrgUnits.map { it.uid() }
                )
                presenter.setOrgUnitFilters(selectedOrgUnits)
            }
            .build()
            .show(parentFragmentManager, "OUTreeFragment")
    }
}
