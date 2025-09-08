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
import org.dhis2.community.tasking.repositories.TaskingRepository
import javax.inject.Inject


class TaskingFragment : Fragment(), TaskingView {

    private lateinit var presenter: TaskingPresenter


    /*@Inject
    lateinit var repository: TaskingRepository
    @Inject
    lateinit var creationEvaluator: CreationEvaluator*/

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter = TaskingPresenter()



        /*creationEvaluator.createTasks(
            repository.getTaskingConfig().taskProgramConfig.first().programUid,
            repository.getTaskingConfig().taskProgramConfig.first().teiTypeUid,
            repository.getTaskingConfig().taskConfigs.first().programUid
            )*/
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
