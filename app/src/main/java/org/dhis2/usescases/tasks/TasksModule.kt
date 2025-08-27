package org.dhis2.usescases.tasks

import dagger.Module
import dagger.Provides
import org.dhis2.commons.di.dagger.PerFragment

@Module
class TasksModule(private val view: TaskView) {

    @Provides
    @PerFragment
    fun provideView(): TaskView = view

    @Provides
    @PerFragment
    fun provideViewModelFactory(): TasksViewModelFactory {
        return TasksViewModelFactory()
    }
}
