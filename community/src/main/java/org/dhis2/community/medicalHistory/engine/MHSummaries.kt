package org.dhis2.community.medicalHistory.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.community.medicalHistory.models.MedicalHistoryConfig.MedicalHistoryItem
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.dhis2.community.medicalHistory.utils.Constants

class MHSummaries(
    private val repository: MHRepository
)  {

      fun buildSummary(
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

    private fun buildStatusSummary(
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

    private fun buildListSummary(
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


}