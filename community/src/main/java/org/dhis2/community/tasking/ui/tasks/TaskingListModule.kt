package org.dhis2.community.tasking.ui.tasks

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import org.dhis2.community.tasking.repositories.TaskingRepository
import javax.inject.Singleton

@Module
class TaskingListModule {

    @Provides @Singleton
    fun provideTaskingPresenter(
        repo: TaskingRepository,
        appScope: CoroutineScope
    ) : TaskingContract.Presenter = TaskingPresenter(repo, appScope)

}