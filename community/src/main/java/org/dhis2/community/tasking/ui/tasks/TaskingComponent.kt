package org.dhis2.community.tasking.ui.tasks

import dagger.Component
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.ui.tasks.di.D2Module
import org.hisp.dhis.android.core.D2Manager
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        D2Module::class,
        TaskingDataModule ::class,
        TaskingListModule::class
    ]
)

interface TaskingComponent{
    fun taskingPresenter() : TaskingContract.Presenter
    fun taskingRepository() : TaskingRepository
}