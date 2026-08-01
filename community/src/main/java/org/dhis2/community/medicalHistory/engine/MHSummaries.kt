package org.dhis2.community.medicalHistory.engine

import org.dhis2.community.medicalHistory.models.MedicalHistoryConfig.MedicalHistoryItem
import org.dhis2.community.medicalHistory.repository.MHRepository

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
                    deUid.uid,
                    source.sourceProgramStageUid
                )?.let {
                    values.add(it.lowercase())
                }
            }
        }

        item.summary.rules
            ?.sortedBy { it.priority }
            ?.forEach { rule ->

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

        //val summary = item.summary
        //val display = summary.display

        val collected = mutableListOf<String>()

        val visibleItems = mutableListOf<String>()
        val allItems = mutableListOf<String>()

        var hiddenCount = 0
        var deIndex = 0

        item.source.forEach { source ->

            source.sourceDEs.forEach { deUid ->

                val value = repository.getLatestValueFromProgram(
                    teiUid,
                    source.sourceProgramUid,
                    deUid.uid,
                    source.sourceProgramStageUid
                ) ?: run {
                    deIndex++
                    return@forEach
                }

                val label = deUid.label?: repository
                    .getDataElementDisplayName(deUid.uid)
                    .trim()
                    .removeSuffix("?")

                val mappedValue = item.summary.mappings
                    ?.firstOrNull {
                        it.sourceValue.equals(value, true)
                    }?.targetValue ?: value

                val text = item.summary.format
                    .replace("{label}", label)
                    .replace("{value}", mappedValue)

                if (item.summary.display == null){
                    collected.add(text)
                } else {
                    if (deIndex < item.summary.display.visibleItems) {
                        visibleItems.add(text)
                    } else hiddenCount++
                }

                //collected.add(text)
                deIndex++
            }
        }

        if (item.summary.display == null) {
            return if (collected.isEmpty()) {
                item.summary.emptyValue
            } else collected.distinct().joinToString( item.summary.separator )
        }

        if(visibleItems.isEmpty() && hiddenCount == 0){
            return item.summary.emptyValue
        }

        return buildString {

            append(visibleItems.joinToString( item.summary.separator))

            if (hiddenCount > 0) {

                if (visibleItems.isNotEmpty()){
                    append(item.summary.separator)
                }

                append(
                    item.summary.display.overflowFormat.replace(
                        "{remaining}",
                        hiddenCount.toString()
                    )
                )
            }
        }

       /* return if (collected.isEmpty())
            item.summary.emptyValue
        else
            collected.distinct()
                .joinToString(item.summary.separator)*/
    }


}