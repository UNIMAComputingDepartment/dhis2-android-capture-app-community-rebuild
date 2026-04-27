package org.dhis2.community.tasking.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.community.tasking.notifications.NotificationChannelManager
import org.dhis2.community.tasking.notifications.TaskReminderScheduler
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicator
import org.hisp.dhis.mobile.ui.designsystem.component.ProgressIndicatorType
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor


class TaskingFragment(
    private val onTaskClick: ((Context, String, String, String) -> Unit)? = null
) : Fragment(), TaskingView {
    private lateinit var repository: TaskingRepository
    private lateinit var d2: D2
    //private lateinit var presenter: TaskingPresenter
    private lateinit var filterRepository: TaskFilterRepository

    private lateinit var viewModel: TaskingViewModel
    val showFilterBar = MutableLiveData(false)

    private val snackbarHostState = SnackbarHostState()

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

        // Create notification channels for task reminders (API 26+)
        NotificationChannelManager.createNotificationChannels(requireContext())

        // Schedule task reminders in onStart() instead to avoid blocking Fragment init
    }

    override fun onStart() {
        super.onStart()
        // Schedule task reminders asynchronously on background thread
        lifecycleScope.launch {
            TaskReminderScheduler.scheduleTaskReminder(requireContext())
        }
    }

    @Composable
    private fun SnackbarHostComposable() {
        SnackbarHost(hostState = snackbarHostState) { snackbarData ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Snackbar(
                    modifier = Modifier.widthIn(max = 360.dp),
                    containerColor = TextColor.OnSurface,
                    content = {
                        Text(
                            text = snackbarData.visuals.message,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DHIS2Theme {
                    TaskingScreenWrapper()
                }
            }
        }
    }

    @Composable
    private fun TaskingScreenWrapper() {
        //  FORCE: Observe loading state with key to force recomposition
        val loading by viewModel.loading.collectAsState()

        Log.d("TaskingFragment", "TaskingScreenWrapper recomposed - loading: $loading")

        if (loading) {
            //  FORCE: Show loading animation using DHIS2 ProgressIndicator (same as Programs tab)
            Log.d("TaskingFragment", "SHOWING LOADING INDICATOR")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                ProgressIndicator(type = ProgressIndicatorType.CIRCULAR_SMALL)
            }
        } else {
            //  Show tasks list when loading complete
            Log.d("TaskingFragment", "SHOWING TASKS LIST")
            Scaffold(
                snackbarHost = { SnackbarHostComposable() }
            ) { paddingValues ->
                TaskingUi(
                    onTaskClick = {
                        onTaskClicked(it)
                    },
                    viewModel = viewModel,
                    filterState = viewModel.filterState,
                    onOrgUnitFilterSelected = {
                        openOrgUnitTreeSelector()
                    },
                    showFilterBar = showFilterBar.observeAsState(false).value
                )
            }
        }
    }

    private fun onTaskClicked(task: TaskingUiModel) {
        lifecycleScope.launch {
            val canOpen = withContext(Dispatchers.IO) { viewModel.canOpenTask(task) }
            if (canOpen) {
                withContext(Dispatchers.Main) {
                    onTaskClick?.invoke(
                        requireContext(),
                        task.sourceTeiUid,
                        task.sourceProgramUid,
                        task.sourceEnrollmentUid
                    )
                    //  When user returns, onResume() triggers refreshTaskList()
                }
            } else {
                val message = "Task cannot be opened"
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //  Trigger reload with loading animation
        refreshTaskList()
    }

    private fun refreshTaskList() {
        lifecycleScope.launch {
            android.util.Log.d("TaskingFragment", "Refreshing task list...")
            viewModel.reloadTasks()
        }
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