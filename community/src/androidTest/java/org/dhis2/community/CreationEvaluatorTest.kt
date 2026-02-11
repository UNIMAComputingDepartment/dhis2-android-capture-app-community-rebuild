package org.dhis2.community
//
//
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import org.dhis2.community.tasking.engine.CreationEvaluator
//import org.dhis2.community.tasking.repositories.TaskingRepository
//import org.hisp.dhis.android.core.D2
//import org.junit.Assert.assertTrue
//import org.junit.Test
//import org.junit.runner.RunWith
//import javax.inject.Inject
//
//@RunWith(AndroidJUnit4::class)
//class CreationEvaluatorTest {
//
//    @Inject
//    lateinit var d2: D2
//
//    @Inject
//    lateinit var repository: TaskingRepository
//
//    @Test
//    fun testCreateTasks() {
//        val evaluator = CreationEvaluator(repository)
//
//        val tasks = evaluator.evaluateForCreation(
//            repository.getTaskingConfig().taskProgramConfig.first().programUid,
//            repository.getTaskingConfig().taskProgramConfig.first().teiTypeUid,
//            repository.getTaskingConfig().taskConfigs.first().programUid,
//            sourceTieUid = "",
//            sourceTieOrgUnitUid = "",
//            sourceTieProgramEnrollment = "",
//        )
//
//        assertTrue("Expected tasks to be created", tasks.isNotEmpty())
//        println("✅ Created tasks: $tasks")
//    }
//}
