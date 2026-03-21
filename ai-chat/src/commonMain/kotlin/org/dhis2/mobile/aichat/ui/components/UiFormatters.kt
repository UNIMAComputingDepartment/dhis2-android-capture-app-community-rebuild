package org.dhis2.mobile.aichat.ui.components

import androidx.compose.runtime.Composable
import dhis2_android_capture_app.ai_chat.generated.resources.Res
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_10_years
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_12_months
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_3_years
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_4_quarters
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_4_years
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_5_years
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_6_months
import dhis2_android_capture_app.ai_chat.generated.resources.period_last_year
import dhis2_android_capture_app.ai_chat.generated.resources.period_this_quarter
import dhis2_android_capture_app.ai_chat.generated.resources.period_this_year
import org.jetbrains.compose.resources.stringResource

private val dataTypeLabels =
    mapOf(
        "aggregate" to "Aggregate",
        "indicator" to "Indicator",
        "programIndicator" to "Program Indicator",
        "event" to "Event",
        "tracker" to "Tracker",
    )

fun formatDataTypeLabel(value: String?): String =
    value?.let { dataTypeLabels[it] ?: formatToken(it) } ?: "-"

@Composable
fun formatPeriodLabel(value: String?): String =
    when (value) {
        "LAST_12_MONTHS" -> stringResource(Res.string.period_last_12_months)
        "THIS_YEAR" -> stringResource(Res.string.period_this_year)
        "LAST_YEAR" -> stringResource(Res.string.period_last_year)
        "LAST_6_MONTHS" -> stringResource(Res.string.period_last_6_months)
        "LAST_4_QUARTERS" -> stringResource(Res.string.period_last_4_quarters)
        "THIS_QUARTER" -> stringResource(Res.string.period_this_quarter)
        "LAST_3_YEARS" -> stringResource(Res.string.period_last_3_years)
        "LAST_4_YEARS" -> stringResource(Res.string.period_last_4_years)
        "LAST_5_YEARS" -> stringResource(Res.string.period_last_5_years)
        "LAST_10_YEARS" -> stringResource(Res.string.period_last_10_years)
        null -> "-"
        else -> formatToken(value)
    }

fun formatToken(value: String): String =
    value
        .replace("_", " ")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { ch -> ch.titlecase() }
        }
