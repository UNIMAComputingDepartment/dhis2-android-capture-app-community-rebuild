package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.dhis2.community.medicalHistory.utils.Constants

class MHSummaries(
)  {
    suspend fun buildImmunizationSummaries(
        teiUid: String, repository: MHRepository, baseProgramUid: String
    ): Map<String, String> = withContext(Dispatchers.IO) {

        val config =
            repository.getMedicalHistoryConfigs().medicalHistoryConfig.filter { it.name == Constants.IMMUNIZATION }

        val summaries = mutableMapOf<String, String>()

        config.forEach { item ->

            val collected = mutableListOf<String>()

            item.source.forEach { source ->
                val programUid = source.sourceProgramUid
                val programStageUid = source.sourceProgramStageUid

                source.sourceDEs.forEach { deUId ->

                    val value = repository.getLatestValueFromProgram(
                        teiUid = teiUid,
                        programUid = programUid,
                        deUid = deUId,
                        programStageUid = programStageUid
                    )
                    print("program value : $value")

                    if (!value.isNullOrBlank()) {
                        val formName = repository.getDataElementDisplayName(deUId)
                        collected.add("$formName: $value")
                    }
                }

                val summaryText = if (collected.isEmpty()) {
                    "None recorded"
                } else {
                    collected.distinct().joinToString("\n")
                }
                summaries[item.targetDE] = summaryText

                repository.updateSummaryValues(
                    teiUid = teiUid, baseProgramUid = baseProgramUid, summaries = summaries
                )
            }
        }
        summaries
    }

    suspend fun buildHIVStatusSummary(
        teiUid: String, repository: MHRepository, baseProgramUid: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val config =
            repository.getMedicalHistoryConfigs().medicalHistoryConfig.filter { it.name == Constants.HIV_STATUS_CONF }

        val summaries = mutableMapOf<String, String>()

        config.forEach { item ->

            var hasPositive = false
            var hasNegative = false

            item.source.forEach { source ->

                val programUid = source.sourceProgramUid
                val programUStageUid = source.sourceProgramStageUid

                source.sourceDEs.forEach { deUid ->
                    val value = repository.getLatestValueFromProgram(
                        teiUid = teiUid,
                        programUid = programUid,
                        deUid = deUid,
                        programStageUid = programUStageUid
                    )

                    when {
                        value.isNullOrBlank() -> {}

                        value.equals(Constants.UNKNOWN_STATUS, ignoreCase = true) -> {}

                        value.lowercase() in listOf(
                            Constants.YES,
                            Constants.ONE,
                            Constants.POSITIVE,
                            Constants.TRUE
                        ) -> {
                            hasPositive = true
                        }

                        else -> {
                            hasNegative = true
                        }
                    }
                }
            }

            val summaryText = when {
                //hasPositive && hasNegative -> ("${Constants.HIV_STATUS}: ${Constants.UNKNOWN_STATUS}")
                hasPositive -> ("${Constants.HIV_STATUS}: ${Constants.POST_STATUS}")
                hasNegative -> ("${Constants.HIV_STATUS}: ${Constants.NEG_STATUS}")
                else -> ("${Constants.HIV_STATUS}: ${Constants.UNKNOWN_STATUS}")
            }

            summaries[item.targetDE] = summaryText
        }

        repository.updateSummaryValues(
            teiUid = teiUid, baseProgramUid = baseProgramUid, summaries = summaries
        )

        summaries
    }
}