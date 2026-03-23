package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.repository.MHRepository

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
        baseProgramUid: String
    ) = withContext(ioDispatchers) {
        runInternal(
            teiUid = teiUid,
            baseProgramUid = baseProgramUid
        )
    }

    fun runAsync(
        teiUid: String,
        baseProgramUid: String
    ): Job = scope.launch {
        runInternal(
            teiUid = teiUid,
            baseProgramUid = baseProgramUid
        )
    }

    private suspend fun runInternal(
        teiUid: String,
        baseProgramUid: String,
    ) {

        val configBaseProgramUid = repository.getMedicalHistoryConfigs().baseProgram.
            firstOrNull()?.baseProgramUid

        if (baseProgramUid == configBaseProgramUid){
            try {


                summariesBuilder.buildImmunizationSummaries(
                    teiUid = teiUid,
                    repository = repository,
                    baseProgramUid = baseProgramUid
                )

                summariesBuilder.buildHIVStatusSummary(
                    teiUid = teiUid,
                    repository = repository,
                    baseProgramUid = baseProgramUid
                )


            } catch (t: Throwable) {
                throw t
            }
        } else return
    }
}