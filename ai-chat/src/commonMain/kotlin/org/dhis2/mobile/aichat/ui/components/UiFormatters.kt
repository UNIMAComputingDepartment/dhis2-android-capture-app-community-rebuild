package org.dhis2.mobile.aichat.ui.components

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

fun formatToken(value: String): String =
    value
        .replace("_", " ")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { ch -> ch.titlecase() }
        }

