package org.dhis2.community.tasking.repositories
//
//import dagger.Module
//import dagger.Provides
//import javax.inject.Singleton
//import org.dhis2.community.tasking.ui.TaskingView
//import org.dhis2.community.tasking.ui.TaskingPresenter
//import org.dhis2.community.tasking.filters.TaskFilterRepository
//import org.dhis2.commons.filters.FilterManager
//
//@Module
//class TaskingModule {
//
//    @Provides
//    @Singleton
//    fun provideView(view: TaskingView): TaskingView = view
//
//    @Provides
//    @Singleton
//    fun provideTaskFilterRepository(): TaskFilterRepository = TaskFilterRepository()
//
//    @Provides
//    @Singleton
//    fun provideFilterManager(): FilterManager = FilterManager.getInstance()
//
//    @Provides
//    @Singleton
//    fun providePresenter(
//        filterRepository: TaskFilterRepository,
//        filterManager: FilterManager
//    ): TaskingPresenter {
//        return TaskingPresenter(filterRepository, filterManager)
//    }
//
//    @Provides
//    @Singleton
//    fun provideTaskingRepository(d2: org.hisp.dhis.android.core.D2): TaskingRepository {
//        return TaskingRepository(d2)
//    }
//}