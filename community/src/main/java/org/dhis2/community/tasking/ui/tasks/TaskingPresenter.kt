package org.dhis2.community.tasking.ui.tasks

import kotlinx.coroutines.*
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.repositories.TaskingRepository

class TaskingPresenter(
    private val repository: TaskingRepository,
    private val externalScope: CoroutineScope
) : TaskingContract.Presenter{

    private var view: TaskingContract.View? = null
    private var job: Job? = null

    override fun attach(view: TaskingContract.View) {
        this.view = view
    }

    override fun detach() {
        job?.cancel()
        view = null
    }

    override fun loadTasks(orgUnit: String?) {
        view?.showLoading()
        job?.cancel()
        job = externalScope.launch(Dispatchers.IO){
            try{
                val tasks = if (orgUnit.isNullOrBlank())
                    repository.getAllTasks()

                else
                    repository.getTasksPerOrgUnit(orgUnit)

                withContext(Dispatchers.Main){
                    view?.showTasks(tasks)
                }
            } catch (e : Exception){
                withContext(Dispatchers.Main){
                    view?.showError(e.localizedMessage?: "Failed to load Tasks")
                }
            }
        }
    }

    override fun onTaskClick(task: Task) {
       print(task.name)
    }

    override fun onRefresh(orgUnit: String?) {
        loadTasks(orgUnit = orgUnit)
    }
}
