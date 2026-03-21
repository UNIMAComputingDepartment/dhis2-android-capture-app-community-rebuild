package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.hisp.dhis.android.core.D2

class MHEngine(
    private val repository: MHRepository,
    //private val summariesBuilder: MHSummaries,
    private val ioDispatchers: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = MHEngine::class.java.simpleName
    private val summariesBuilder = MHSummaries()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatchers)

    fun clear() = (scope.coroutineContext[Job])?.cancel()

    suspend fun run(
        teiUid: String,
        programUid: String
    ) = withContext(ioDispatchers){
        runInternal(teiUid, programUid)
    }

    fun runAsync(
        teiUid: String,
        programUid: String
    ): Job = scope.launch {
        runInternal(
            teiUid = teiUid,
            programUid = programUid
        )
    }

    private suspend fun runInternal(
        teiUid: String,
        programUid: String,
    ){

        try {



            repository.updateSummaryValues(
                teiUid = teiUid,
                programUid = programUid,
                summaries = summariesBuilder.buildImmunizationSummaries(
                    teiUid = teiUid,
                    repository = repository
                )
            )

            repository.updateSummaryValues(
                teiUid = teiUid,
                programUid = programUid,
                summaries = summariesBuilder.buildHIVStatusSummary(
                    teiUid = teiUid,
                    repository = repository
                )
            )

        } catch (t: Throwable){
            throw t
        }
    }
}