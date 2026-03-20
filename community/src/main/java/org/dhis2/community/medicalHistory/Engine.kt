package org.dhis2.community.medicalHistory

import androidx.constraintlayout.compose.ConstraintSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hisp.dhis.android.core.D2

class Engine(
    private val d2: D2,
    private val repository: Repository,
    private val summariesBuilder: Summaries,
    private val ioDispatchers: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = Engine::class.java.simpleName
    private val scope = CoroutineScope(SupervisorJob() + ioDispatchers)

    fun clear() = (scope.coroutineContext[Job])?.cancel()

    suspend fun run(
        teiUid: String,
        summariesEventUid: String
    ) = withContext(ioDispatchers){
        runInternal(teiUid, summariesEventUid)
    }

    fun runAsync(
        teiUid: String,
        summariesEventUid: String
    ): Job = scope.launch {
        runInternal(teiUid, summariesEventUid)
    }

    private suspend fun runInternal(
        teiUid: String,
        summariesEventUid: String
    ){
        try {
            val summaries = summariesBuilder.buildImmunizationSummaries(
                teiUid = teiUid,
                repository = repository
            )

            repository.updateImmunizationSummaryValues(
                eventUid = summariesEventUid,
                summaries = summaries
            )
        } catch (t: Throwable){
            throw t
        }
    }
}