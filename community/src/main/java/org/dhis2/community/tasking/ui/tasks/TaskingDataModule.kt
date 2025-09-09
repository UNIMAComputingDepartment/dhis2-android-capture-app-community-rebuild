package org.dhis2.community.tasking.ui.tasks

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.*
import kotlinx.coroutines.SupervisorJob
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import javax.inject.Singleton

@Module
class TaskingDataModule {
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideTaskingRepository(d2: D2) : TaskingRepository =
        TaskingRepository(d2)

}
