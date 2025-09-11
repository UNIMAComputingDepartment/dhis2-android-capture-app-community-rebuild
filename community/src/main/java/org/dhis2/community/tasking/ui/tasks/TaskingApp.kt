package org.dhis2.community.tasking.ui.tasks

import android.app.Application
import org.dhis2.community.tasking.ui.tasks.di.D2Module

class TaskingApp : Application() {
    lateinit var taskingComponent: TaskingComponent
    private set

    override fun onCreate(){
        super.onCreate()
        taskingComponent = DaggerTaskingComponent.builder()
            .taskingDataModule(TaskingDataModule())
            .taskingListModule(TaskingListModule())
            .d2Module(D2Module())
            .build()
    }
}