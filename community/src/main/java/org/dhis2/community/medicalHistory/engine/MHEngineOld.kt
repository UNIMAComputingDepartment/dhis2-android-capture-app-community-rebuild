package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.repository.MHRepository

class MHEngineOld(
    private val repository: MHRepository,
    //private val summariesBuilder: MHSummaries,
    private val ioDispatchers: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = MHEngineOld::class.java.simpleName
    private val summariesBuilder = MHSummaries(repository)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatchers)

    fun clear() = (scope.coroutineContext[Job])?.cancel()

    suspend fun run(
        teiUid: String,
        baseProgramUid: String,
        baseProgramStage: String
    ) = withContext(ioDispatchers) {
        runInternal(
            teiUid = teiUid,
            baseProgramUid = baseProgramUid,
            baseProgramStage = baseProgramStage
        )
    }

    fun runAsync(
        teiUid: String,
        baseProgramUid: String,
        baseProgramStage: String
    ): Job = scope.launch {
        runInternal(
            teiUid = teiUid,
            baseProgramUid = baseProgramUid,
            baseProgramStage = baseProgramStage
        )
    }

    private fun runInternal(
        teiUid: String,
        baseProgramUid: String,
        baseProgramStage: String
    ) {

        val configBaseProgramUid =
            repository.getMedicalHistoryConfigs().baseProgram.firstOrNull()?.baseProgramUid

        val configBaseProgramStage =
            repository.getMedicalHistoryConfigs().baseProgram.firstOrNull()?.baseProgramStageUid

        if (baseProgramUid == configBaseProgramUid && baseProgramStage == configBaseProgramStage) {
            try {

               /* summariesBuilder.buildImmunizationSummaries(
                    teiUid = teiUid, baseProgramUid = baseProgramUid
                )

                summariesBuilder.buildHIVStatusSummary(
                    teiUid = teiUid, baseProgramUid = baseProgramUid
                )

                summariesBuilder.buildNCDSummaries(
                    teiUid = teiUid, baseProgramUid = baseProgramUid
                )*/

                summariesBuilder.buildSummary(
                    teiUid = teiUid,
                    baseProgramUid = baseProgramUid
                )

            } catch (t: Throwable) {
                throw t
            }
        } else return
    }
}