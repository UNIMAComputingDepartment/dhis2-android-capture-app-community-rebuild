package org.dhis2.community.tasking.di

import org.dhis2.community.tasking.repositories.TaskingRepository

interface TaskingEntryPoint {
    fun getTaskingRepository(): TaskingRepository
}
