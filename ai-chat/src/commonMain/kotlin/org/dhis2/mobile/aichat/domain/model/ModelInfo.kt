package org.dhis2.mobile.aichat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val name: String,
    val modifiedAt: String,
    val size: Long,
    val details: Map<String, String> = emptyMap(),
)

