package org.dhis2.mobile.aichat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DataDiagnostics(
    val rowCount: Int,
    val headerCount: Int,
    val hasData: Boolean,
    val dataType: String,
    val rowsTruncated: Boolean,
)

