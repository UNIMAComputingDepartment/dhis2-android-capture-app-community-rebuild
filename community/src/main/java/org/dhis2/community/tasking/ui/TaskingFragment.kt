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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme


class TaskingFragment(
    private val onTaskClick: ((Context, String, String, String) -> Unit)? = null
) : Fragment(), TaskingView {
    private lateinit var repository: TaskingRepository
    private lateinit var d2: D2
    //private lateinit var presenter: TaskingPresenter
    private lateinit var filterRepository: TaskFilterRepository

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
        viewModel = TaskingViewModel(repository, filterRepository)
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

                DHIS2Theme {
                    TaskingUi(
                        onTaskClick = {
                            onTaskClicked(it)
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
    }

    private fun onTaskClicked(task: TaskingUiModel) {
        lifecycleScope.launch { val canOpen = withContext(Dispatchers.IO) { viewModel.canOpenTask(task) }
            if (canOpen) {
                withContext(Dispatchers.Main) {
                    onTaskClick?.invoke(
                        requireContext(),
                        task.sourceTeiUid,
                        task.sourceProgramUid,
                        task.sourceEnrollmentUid
                    )
                }
            } else {
                // Display snackbar message that task cannot be opened
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
        showFilterBar.value = showFilterBar.value != true
    }
}
