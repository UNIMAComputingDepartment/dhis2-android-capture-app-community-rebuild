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

            val results = mutableListOf<Boolean>()

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

                    if (value.isNullOrBlank()) {
                        val nullSummaryText = ("${Constants.HIV_STATUS}: ${Constants.UNKNOWN_STATUS}")
                        summaries[item.targetDE] = nullSummaryText
                        return@forEach
                    }

                    val isPositive = when (value.lowercase()) {
                        Constants.YES, Constants.ONE, Constants.POSITIVE, Constants.TRUE -> true
                        Constants.UNKNOWN_STATUS -> {
                            val nullSummaryText = ("${Constants.HIV_STATUS}: ${Constants.UNKNOWN_STATUS}")
                            summaries[item.targetDE] = nullSummaryText
                            return@forEach
                        }
                        else -> false
                    }
                    results.add(isPositive)
                }

                val hasPositive = results.any { it }
                val summaryText = when (hasPositive){
                    true -> ("${Constants.HIV_STATUS}: ${Constants.POST_STATUS}")
                    false -> ("${Constants.HIV_STATUS}: ${Constants.NEG_STATUS}")
                }

                summaries[item.targetDE] = summaryText

                repository.updateSummaryValues(
                    teiUid = teiUid, baseProgramUid = baseProgramUid, summaries = summaries
                )
            }
        }

        summaries
    }
}