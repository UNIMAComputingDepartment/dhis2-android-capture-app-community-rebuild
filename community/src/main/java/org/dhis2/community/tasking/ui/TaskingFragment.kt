package org.dhis2.community.tasking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.dhis2.community.tasking.engine.TaskingPresenter

class TaskingFragment : Fragment(), TaskingView {

    private lateinit var presenter: TaskingPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter = TaskingPresenter(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TaskingUi(tasks = presenter.tasks)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun showSyncDialog() {
    }
}
