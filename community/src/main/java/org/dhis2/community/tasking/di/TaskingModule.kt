package org.dhis2.community.tasking.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.dhis2.community.tasking.engine.CreationEvaluator
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.ui.TaskingView
import org.dhis2.community.tasking.ui.TaskingPresenter
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
        taskingView: TaskingView
    ): TaskingView = taskingView

    @Provides
    @Singleton
    fun providePresenter(tasksPresenter: TaskingPresenter): TaskingPresenter = tasksPresenter


    /*@Provides
    @Singleton
    fun provideCreationEvaluator(
        creationEvaluator: CreationEvaluator
    ): CreationEvaluator = creationEvaluator*/

}