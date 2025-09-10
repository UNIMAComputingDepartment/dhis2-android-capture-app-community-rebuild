package org.dhis2.community.tasking.ui.tasks.di

import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import org.hisp.dhis.android.core.D2;
import org.hisp.dhis.android.core.D2Manager

@Module
class D2Module {

    @Provides
    @Singleton
    fun provideD2(): D2 {
        return D2Manager.getD2()
    }
}

