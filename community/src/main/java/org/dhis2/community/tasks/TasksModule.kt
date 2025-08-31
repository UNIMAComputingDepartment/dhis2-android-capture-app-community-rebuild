package org.dhis2.community.tasks

import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class TasksModule(private val view: TaskView) {

    @Provides
    @Singleton
    fun provideView(): TaskView = view

    @Provides
    @Singleton
    fun providePresenter(view: TaskView): TasksPresenter {
        return TasksPresenter(view)
    }
}
