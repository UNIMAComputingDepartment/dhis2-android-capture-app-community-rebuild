package org.dhis2.community.tasking.ui

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.filters.TaskFilterRepository
import org.dhis2.commons.filters.FilterManager
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.dhis2.community.tasking.filters.models.TaskFilterModel
import org.dhis2.community.tasking.repositories.TaskingRepository
import javax.inject.Inject
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.dhis2.community.tasking.engine.DefaultingEvaluator

class TaskingPresenter @Inject constructor(
    private val filterRepository: TaskFilterRepository,
    private val filterManager: FilterManager,
    private val repository: TaskingRepository,
    private val defaultingEvaluator: DefaultingEvaluator
) {
    private var view: TaskingView? = null
    private val disposable = CompositeDisposable()

    init {
        Log.d("TaskingPresenter", "TaskingPresenter initialized")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun init(view: TaskingView) {
        Log.d("TaskingPresenter", "Initializing presenter with view")
        this.view = view
        loadData()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onResume() {
        Log.d("TaskingPresenter", "onResume called - refreshing data")
        loadData() // Refresh data when screen is resumed
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadData() {
        try {
            Log.d("TaskingPresenter", "Loading fresh task data")
            // Run defaulting logic for all open tasks (overdue by 2+ days or trigger no longer met)
            val programUid = repository.getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid ?: ""
            val teiTypeUid = repository.getTaskingConfig().taskProgramConfig.firstOrNull()?.teiTypeUid ?: ""
            val orgUnitUid = repository.currentOrgUnits.firstOrNull() ?: ""
            val enrollmentUid = "" // If you have a way to get the current enrollment, use it here
            defaultingEvaluator.defaultTasks(
                targetProgramUid = programUid,
                sourceTieUid = null,
                sourceTieOrgUnitUid = orgUnitUid,
                sourceTieProgramEnrollment = enrollmentUid
            )
            val freshTasks = repository.getAllTasks().map { task ->
                TaskingUiModel(task, repository.getOrgUnit(task.teiUid), repository)
            }
            Log.d("TaskingPresenter", "Loaded ${freshTasks.size} tasks")

            // Update view with fresh task data
            view?.showTasks(freshTasks)

            // Notify filter manager to refresh filters
            filterManager.publishData()

        } catch (e: Exception) {
            Log.e("TaskingPresenter", "Error loading tasks", e)
        }
    }

    fun clear() {
        Log.d("TaskingPresenter", "Cleaning up presenter")
        disposable.clear()
        view = null
    }

    fun setOrgUnitFilters(selectedOrgUnits: List<OrganisationUnit>) {
        val orgUnitUids = selectedOrgUnits.map { it.uid() }.toSet()
        val currentFilter = filterRepository.selectedFilters.value
        filterRepository.updateFilter(currentFilter.copy(orgUnitFilters = orgUnitUids))
    }
}
