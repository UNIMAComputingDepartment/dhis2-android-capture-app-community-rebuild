package org.dhis2.community.tasking.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TaskingFragment : Fragment() {
    @Inject
    lateinit var factory: TaskingViewModelFactory

    private val viewModel: TaskingViewModel by viewModels { factory }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val tasks = viewModel.filteredTasks.collectAsState()
                TaskingUi(
                    tasks = tasks.value,
                    onTaskClick = { task ->
                        // Log navigation details for debugging
                        Timber.d("Navigating to TEI Dashboard - TEI: ${task.teiUid}, Program: ${task.sourceProgramUid}, Name: ${task.sourceProgramName}")

                        // Navigate to TEI Dashboard with the source program context
                        startActivity(Intent(requireContext(), Class.forName("org.dhis2.usescases.teiDashboard.TeiDashboardMobileActivity")).apply {
                            putExtra("TEI_UID", task.teiUid)
                            putExtra("PROGRAM_UID", task.sourceProgramUid)
                            // Ensure we don't use analytics mode since we're viewing a specific TEI
                            putExtra("FROM_ANALYTICS", false)
                        })
                    },
                    viewModel = viewModel,
                    filterState = viewModel.filterState
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
