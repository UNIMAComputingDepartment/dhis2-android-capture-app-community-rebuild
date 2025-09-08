package org.dhis2.community.tasking.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.dhis2.community.tasking.engine.CreationEvaluator
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.ui.TaskView
import org.dhis2.community.tasking.ui.TasksPresenter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TaskingModule {

   /* @Provides
    @Singleton
    fun provideTaskingRepository(
        taskingRepository: TaskingRepository
    ): TaskingRepository = taskingRepository*/

    @Provides
    @Singleton
    fun provideView(
        taskView: TaskView
    ): TaskView = taskView

    @Provides
    @Singleton
    fun providePresenter(tasksPresenter: TasksPresenter): TasksPresenter = tasksPresenter


    /*@Provides
    @Singleton
    fun provideCreationEvaluator(
        creationEvaluator: CreationEvaluator
    ): CreationEvaluator = creationEvaluator*/

}