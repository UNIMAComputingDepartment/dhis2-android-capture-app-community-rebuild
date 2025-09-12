package org.dhis2.community.tasking.di

import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
//
//interface TaskingComponentProvider {
//    fun provideTaskingEntryPoint(): TaskingEntryPoint
//}
//
//class TaskingComponentProviderImpl(
//    private val d2: D2? = null
//) : TaskingComponentProvider {
//    override fun provideTaskingEntryPoint(): TaskingEntryPoint {
//        return object : TaskingEntryPoint {
//            override fun getTaskingRepository(): TaskingRepository {
//                requireNotNull(d2) { "D2 instance required for real data" }
//                return TaskingRepository(d2)
//            }
//        }
//    }
//}
