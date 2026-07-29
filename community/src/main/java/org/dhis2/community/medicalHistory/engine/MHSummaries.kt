package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.models.MedicalHistoryConfig.MedicalHistoryItem
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.dhis2.community.medicalHistory.utils.Constants

class MHSummaries(
    private val repository: MHRepository
)  {

    suspend fun buildSummary(
        teiUid: String,
        baseProgramUid: String
    ): Map<String, String> {

        val configs = repository
            .getMedicalHistoryConfigs()
            .medicalHistoryConfig

        val summaries = mutableMapOf<String, String>()

        configs.forEach { item ->

            val summary = when(item.summary.type){

                MedicalHistoryItem.SummaryConfig.SummaryType.LIST ->
                    buildListSummary(item, teiUid)

                MedicalHistoryItem.SummaryConfig.SummaryType.STATUS ->
                    buildStatusSummary(item, teiUid)
            }

            summaries[item.targetDE] = summary
        }

        repository.updateSummaryValues(
            teiUid,
            baseProgramUid,
            summaries
        )

        return summaries
    }

    private suspend fun buildStatusSummary(
        item: MedicalHistoryItem,
        teiUid: String
    ): String {

        val values = mutableListOf<String>()

        item.source.forEach { source ->

            source.sourceDEs.forEach { deUid ->

                repository.getLatestValueFromProgram(
                    teiUid,
                    source.sourceProgramUid,
                    deUid,
                    source.sourceProgramStageUid
                )?.let {
                    values.add(it.lowercase())
                }
            }
        }

        item.summary.rules?.forEach { rule ->

            if (values.any { value ->
                    rule.values.any {
                        it.equals(value, true)
                    }
                }) {
                return rule.result
            }
        }

        return item.summary.emptyValue
    }

    private suspend fun buildListSummary(
        item: MedicalHistoryItem,
        teiUid: String
    ): String {

        val collected = mutableListOf<String>()

        item.source.forEach { source ->

            source.sourceDEs.forEach { deUid ->

                val value = repository.getLatestValueFromProgram(
                    teiUid,
                    source.sourceProgramUid,
                    deUid,
                    source.sourceProgramStageUid
                ) ?: return@forEach

                val label = repository
                    .getDataElementDisplayName(deUid)
                    .trim()
                    .removeSuffix("?")

                val mappedValue = item.summary.mappings
                    .firstOrNull {
                        it.sourceValue.equals(value, true)
                    }?.targetValue ?: value

                val text = item.summary.format
                    .replace("{label}", label)
                    .replace("{value}", mappedValue)

                collected.add(text)
            }
        }

        return if (collected.isEmpty())
            item.summary.emptyValue
        else
            collected.distinct()
                .joinToString(item.summary.separator)
    }
    suspend fun buildImmunizationSummaries(
        teiUid: String, baseProgramUid: String
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
                        val formatedValue = when (value.lowercase()) {
                            Constants.TRUE -> Constants.YES
                            Constants.FALSE -> Constants.NO
                            else -> value
                        }
                        collected.add("$formName: $formatedValue")
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
        teiUid: String, baseProgramUid: String
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
                hasPositive -> (Constants.POST_STATUS)
                hasNegative -> (Constants.NEG_STATUS)
                else -> (Constants.UNKNOWN_STATUS)
            }

            summaries[item.targetDE] = summaryText
        }

        repository.updateSummaryValues(
            teiUid = teiUid, baseProgramUid = baseProgramUid, summaries = summaries
        )

        summaries
    }

    suspend fun buildNCDSummaries(
        teiUid: String, baseProgramUid: String
    ): Map<String, String> = withContext(Dispatchers.IO) {

        val config =
            repository.getMedicalHistoryConfigs().medicalHistoryConfig.filter { it.name == Constants.CHRONIC_CONDITIONS }

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
                        val formatedValue = when (value.lowercase()) {
                            Constants.TRUE -> Constants.YES
                            Constants.FALSE -> Constants.NO
                            else -> value
                        }
                        val formatedFormName = formName.trim().removeSuffix("?")
                        collected.add("$formatedFormName: $formatedValue")
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
}