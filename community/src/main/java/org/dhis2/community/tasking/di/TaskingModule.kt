package org.dhis2.community.tasking.di

import dagger.Module
import dagger.Provides
import org.dhis2.community.tasking.ui.TaskingView
import javax.inject.Singleton

@Module
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



    /*@Provides
    @Singleton
    fun provideCreationEvaluator(
        creationEvaluator: CreationEvaluator
    ): CreationEvaluator = creationEvaluator*/

}