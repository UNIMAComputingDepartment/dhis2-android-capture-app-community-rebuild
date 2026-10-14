package org.dhis2.community.medicalHistory.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.hisp.dhis.android.core.maintenance.D2Error
import timber.log.Timber
import java.time.ZoneId
import java.util.Date

class MHEngine(
    private val repository: MHRepository,
) {

    private val TAG = MHEngine::class.java.simpleName
    private val summariesBuilder = MHSummaries(repository)

    /** Every TEI with an active enrollment in the base program. Time-driven entry point. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun medicalHistoryWorker() = run(scope = null)

    /**
     * Only the given TEIs - the change-driven entry point, used after a sync to derive summaries
     * for what the server actually sent. It intersects with the same eligible set the full sweep
     * uses, so it can never touch a TEI [medicalHistoryWorker] would have skipped.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun medicalHistoryFor(teiUids: Set<String>) {
        if (teiUids.isNotEmpty()) run(scope = teiUids)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun run(scope: Set<String>?){

        var currentTeiUid: String? = null

        try{
            val config = repository.getMedicalHistoryConfigs()
            val baseProgramConfig = config.baseProgram.firstOrNull()
            val baseProgramUid = baseProgramConfig?.baseProgramUid
            val baseProgramStage = baseProgramConfig?.baseProgramStageUid

            if(baseProgramUid.isNullOrEmpty() || baseProgramStage.isNullOrEmpty()){
                return
            }

            val teiUids = repository.getTeiUidsWithActiveEnrollmentForProgram(baseProgramUid)
                .let { eligible -> if (scope == null) eligible else eligible.filter(scope::contains) }

            if (teiUids.isEmpty()) return

            val startDate = Date.from(
                repository.quarterDatesCalculator().first.atStartOfDay(ZoneId.systemDefault()).toInstant()
            )
            val endDate = Date.from(
                repository.quarterDatesCalculator().second.atStartOfDay(ZoneId.systemDefault()).toInstant()
            )

            teiUids.forEach { teiUid ->

                currentTeiUid = teiUid

                val exists =
                    repository.eventExistInQuarter(
                        teiUid = teiUid,
                        programUid = baseProgramUid,
                        programStageUid = baseProgramStage,
                        startDate = startDate,
                        endDate = endDate
                    )

                //TODO:
                // Update the existing event for now
                // When we are done with testing only write once to the event in a quarter

                if (!exists) {
                    repository.createNewEvent(
                        teiUid = teiUid,
                        programUid = baseProgramUid,
                        programStageUid = baseProgramStage,
                        eventDate = startDate
                    )
                }

                summariesBuilder.buildSummary(
                    teiUid = teiUid,
                    baseProgramUid = baseProgramUid
                )
            }
        } catch (throwable: Throwable){
            // D2Error has no message and hides the real failure in originalException(),
            // so unwrap it before logging or the cause is invisible in logcat.
            if (throwable is D2Error) {
                Timber.tag(TAG).e(
                    throwable.originalException(),
                    "Error running medical history worker (tei=%s): %s / %s",
                    currentTeiUid,
                    throwable.errorCode(),
                    throwable.errorDescription(),
                )
            } else {
                Timber.tag(TAG).e(throwable, "Error running medical history worker (tei=%s)", currentTeiUid)
            }
            // Deliberately not rethrown: this sweep shares SyncDataWorker's try block with
            // syncAndDownloadEvents(), so rethrowing here silently skipped the event download.
        }
    }
}