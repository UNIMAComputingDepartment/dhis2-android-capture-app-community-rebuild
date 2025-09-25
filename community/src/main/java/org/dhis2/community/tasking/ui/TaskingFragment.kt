package org.dhis2.community.tasking.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.dhis2.commons.orgunitselector.OUTreeFragment
import androidx.lifecycle.MutableLiveData


class TaskingFragment(
    private val onTaskClick: ((Context, String, String, String) -> Unit)? = null
) : Fragment(), TaskingView {
    private lateinit var repository: TaskingRepository
    private lateinit var d2: D2
    //private lateinit var presenter: TaskingPresenter
    private lateinit var filterRepository: TaskFilterRepository
    private lateinit var filterManager: FilterManager

    private lateinit var viewModel: TaskingViewModel
    val showFilterBar = MutableLiveData(false)

    companion object {
        fun newInstance(onTaskClick: (Context, String, String, String) -> Unit): TaskingFragment {
            return TaskingFragment(onTaskClick)
        }

        fun findInstance(fragmentManager: androidx.fragment.app.FragmentManager): TaskingFragment? {
            return fragmentManager.fragments.filterIsInstance<TaskingFragment>().firstOrNull()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d2 = org.hisp.dhis.android.core.D2Manager.getD2()
        repository = TaskingRepository(d2)
        filterRepository = TaskFilterRepository()
        filterManager = FilterManager.getInstance()
        viewModel = TaskingViewModel(repository, filterRepository, filterManager, d2)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val showFilterBarState = showFilterBar.observeAsState(false).value
                
                TaskingUi(
                    onTaskClick = {
                        if (viewModel.canOpenTask(it)) {
                            onTaskClick?.invoke(
                                requireContext(),
                                it.sourceTeiUid,
                                it.sourceProgramUid,
                                it.sourceEnrollmentUid
                            )
                        } else {
                            // Display snackbar message that task cannot be opened
                            
                        }
                    },
                    viewModel = viewModel,
                    filterState = viewModel.filterState,
                    onOrgUnitFilterSelected = {
                        openOrgUnitTreeSelector()
                    },
                    showFilterBar = showFilterBarState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadTasks()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun showTasks(tasks: List<TaskingUiModel>) {
        viewModel.updateTasks(tasks)
    }

    override fun clearFilters() {
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
                viewModel.setOrgUnitFilters(selectedOrgUnits)
            }
            .build()
            .show(parentFragmentManager, "OUTreeFragment")
    }

    fun toggleFilterBar() {
        showFilterBar.value = !(showFilterBar.value ?: false)
    }
}
