package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.dhis2.community.medicalHistory.utils.Constants

class MHSummaries(
) {
    suspend fun buildImmunizationSummaries(
        teiUid: String,
        repository: MHRepository
    ): Map<String, String> = withContext(Dispatchers.IO) {

        val config = repository.getMedicalHistoryConfigs()
            .medicalHistoryConfig
            .filter {
                it.name == Constants.IMMUNIZATION
            }

        val summaries = mutableMapOf<String, String>()

        config.forEach { item ->

            val collected = mutableListOf<String>()

            item.source.forEach { source ->
                val programUid = source.sourceProgramUid

                source.sourceDEs.forEach { deUId ->

                    val value = repository.getLatestValueFromProgram(
                        teiUid = teiUid,
                        programUid = programUid,
                        deUid = deUId
                    )

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
            }
        }
        summaries
    }

    suspend fun buildHIVStatusSummary(
        teiUid: String,
        repository: MHRepository
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val config = repository.getMedicalHistoryConfigs()
            .medicalHistoryConfig
            .filter { it.name == Constants.HIV_STATUS }

        val summaries = mutableMapOf<String, String>()

        config.forEach { item ->

            val results = mutableListOf<Boolean>()

            item.source.forEach { source ->

                val programUid = source.sourceProgramUid

                source.sourceDEs.forEach { deUid ->
                    val value = repository.getLatestValueFromProgram(
                        teiUid = teiUid,
                        programUid = programUid,
                        deUid = deUid
                    )

                    val isPositive = when (value?.lowercase()) {
                        Constants.YES,
                        Constants.ONE,
                        Constants.POSITIVE,
                        Constants.TRUE
                            -> true

                        else -> false
                    }
                    results.add(isPositive)
                }
            }

            val hasPositive = results.any { it }
            val summaryText = if (hasPositive) {
                Constants.HIV_POST_STATUS
            } else {
                Constants.HIV_NEG_STATUS
            }

            summaries[item.targetDE] = summaryText
        }

        summaries
    }
}