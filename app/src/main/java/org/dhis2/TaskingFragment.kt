package org.dhis2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.ui.tasks.TaskingContract
import org.dhis2.community.tasking.ui.tasks.TaskingScreen

class TaskingFragment(): Fragment() {

    private lateinit var presenter: TaskingContract.Presenter
    private lateinit var repository: TaskingRepository

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        val app = requireActivity().application as App
        val component = app.taskingComponent
        presenter = component.taskingPresenter()
        repository = component.taskingRepository()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TaskingScreen(presenter = presenter)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.detach()
    }
}