package org.dhis2.community.medicalHistory.engine

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

                MedicalHistoryItem.SummaryConfig.SummaryType.CONDITION_LIST ->
                    buildConditionalListSummary(item, teiUid)
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

    private fun buildConditionalListSummary(
        item: MedicalHistoryItem,
        teiUid: String
    ): String {
        val groupedValue = mutableMapOf<String, MutableList<String>>()

        item.source.forEach { source ->

            source.sourceDEs.forEach { deUid ->

                val group = deUid.summaryGroup ?: return@forEach

                repository.getLatestValueFromProgram(
                    teiUid = teiUid,
                    programUid = source.sourceProgramUid,
                    deUid = deUid.uid,
                    programStageUid = source.sourceProgramStageUid
                )?.let {

                    groupedValue
                        .getOrPut(group) { mutableListOf() }
                        .add(it.lowercase())
                }
            }
        }

        if (groupedValue.isEmpty()) {
            return item.summary.emptyValue
        }

        val lines = mutableListOf<String>()

        groupedValue.forEach { (group, values) ->

            val result = evaluateConditions(
                values = values,
                evaluation = item.summary.evaluation
            ) ?: return@forEach

            val mapped = item.summary.mappings
                ?.firstOrNull {
                    it.sourceValue.equals(result, true)
                }?.targetValue ?: result

            lines.add(

                item.summary.format
                    .replace("{label}", group)
                    .replace("{value}", mapped)
            )
        }

        return if (lines.isEmpty()) {
            item.summary.emptyValue
        } else lines.joinToString(item.summary.separator)
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

        val collected = mutableListOf<String>()
        val visibleItems = mutableListOf<String>()

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
    }

    private fun evaluateConditions(
        values: List<String>,
        evaluation: MedicalHistoryItem.SummaryConfig.EvaluationConfig?
    ): String? {

        if (evaluation == null) return null

        val hasTrue = values.any { value ->
            evaluation.trueValues.any { it.equals(value, true) }
        }

        val allTrue = values.all { value ->
            evaluation.trueValues.any { it.equals(value, true) }
        }

        val allFalse = values.all { value ->
            evaluation.falseValues.any { it.equals(value, true) }
        }

        return when (evaluation.strategy){

            MedicalHistoryItem.SummaryConfig.Strategy.ALL_TRUE -> {

                when {
                    allTrue -> Constants.TRUE
                    allFalse -> Constants.FALSE
                    else -> null
                }
            }

            MedicalHistoryItem.SummaryConfig.Strategy.ANY_TRUE -> {

                when {
                    hasTrue -> Constants.TRUE
                    allFalse -> Constants.FALSE
                    else -> null
                }
            }
        }
    }
}