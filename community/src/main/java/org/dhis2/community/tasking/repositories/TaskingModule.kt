package org.dhis2.community.tasking.repositories

import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import org.dhis2.community.tasking.ui.TaskingView
import org.dhis2.community.tasking.engine.TaskingPresenter

@Module
class TaskingModule(private val view: TaskingView) {

    @Provides
    @Singleton
    fun provideView(): TaskingView = view

    @Provides
    @Singleton
    fun providePresenter(view: TaskingView): TaskingPresenter {
        return TaskingPresenter(view)
    }
}
