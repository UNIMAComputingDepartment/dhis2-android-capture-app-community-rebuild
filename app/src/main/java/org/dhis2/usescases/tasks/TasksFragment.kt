package org.dhis2.usescases.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import org.dhis2.R
import org.dhis2.commons.sync.OnDismissListener
import org.dhis2.commons.sync.SyncContext
import org.dhis2.usescases.general.FragmentGlobalAbstract
import org.dhis2.usescases.tasks.ui.TaskScreen
import org.dhis2.utils.granularsync.SyncStatusDialog

class TasksFragment : FragmentGlobalAbstract() {

    private val tasksViewModel: TasksViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val completionRate by tasksViewModel.completionRate.collectAsState()

                TaskScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tasksViewModel.init()
    }

    fun showSyncDialog() {
        SyncStatusDialog.Builder()
            .withContext(this)
            .withSyncContext(SyncContext.GlobalTrackerProgram("tasks"))
            .onDismissListener(
                object : OnDismissListener {
                    override fun onDismiss(hasChanged: Boolean) {
                        if (hasChanged) {
                            tasksViewModel.updateTasks()
                        }
                    }
                }
            )
            .onNoConnectionListener {
                val contextView = activity?.findViewById<View>(R.id.navigationBar)
                Snackbar.make(
                    contextView!!,
                    R.string.sync_offline_check_connection,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .show(FRAGMENT_TAG)
    }

    companion object {
        const val FRAGMENT_TAG = "SYNC"
    }
}