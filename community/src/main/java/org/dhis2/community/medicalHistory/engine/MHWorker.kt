package org.dhis2.community.medicalHistory.engine

import org.dhis2.community.medicalHistory.repository.MHRepository
import timber.log.Timber
import java.time.ZoneId
import java.util.Date

class MHWorker(
    private val repository: MHRepository,
) {

    private val TAG = MHEngine::class.java.simpleName
    private val summariesBuilder = MHSummaries(repository)

    fun medicalHistoryWorker(){

        try{
            val config = repository.getMedicalHistoryConfigs()
            val baseProgramConfig = config.baseProgram.firstOrNull()
            val baseProgramUid = baseProgramConfig?.baseProgramUid
            val baseProgramStage = baseProgramConfig?.baseProgramStageUid

            if(baseProgramUid.isNullOrEmpty() || baseProgramStage.isNullOrEmpty()){
                return
            }

            val teiUids = repository.getTeiUidsWithActiveEnrollmentForProgram(baseProgramUid)
            val startDate = Date.from(
                repository.quarterDatesCalculator().first.atStartOfDay(ZoneId.systemDefault()).toInstant()
            )
            val endDate = Date.from(
                repository.quarterDatesCalculator().second.atStartOfDay(ZoneId.systemDefault()).toInstant()
            )

            teiUids.forEach { teiUid ->


                val exists =
                    repository.eventExistInQuarter(
                        teiUid = teiUid,
                        programUid = baseProgramUid,
                        programStageUid = baseProgramStage,
                        startDate = startDate,
                        endDate = endDate
                    )

                if (exists) {
                    return@forEach
                }

                repository.createNewEvent(
                    teiUid = teiUid,
                    programUid = baseProgramUid,
                    programStageUid = baseProgramStage,
                    eventDate = startDate
                )

                summariesBuilder.buildSummary(
                    teiUid = teiUid,
                    baseProgramUid = baseProgramUid
                )
            }
        } catch (throwable: Throwable){
            Timber.tag(TAG).e(throwable, "Error running medical history worker")
            throw throwable
        }
    }
}