package org.dhis2.mobile.aichat.data.remote.dto

data class HealthResponse(
    val status: String,
    val timestamp: String,
    val version: String,
)

data class ModelsResponse(
    val models: List<ModelInfoDto>,
)

data class ModelInfoDto(
    val name: String,
    val modified_at: String,
    val size: Long,
    val details: Map<String, String> = emptyMap(),
)

data class ChatSessionSummaryDto(
    val id: String,
    val username: String,
    val dataDiagnostics: DataDiagnosticsDto? = null,
    val created_at: String,
    val message_count: Int,
    val last_message_at: String? = null,
)

data class DataDiagnosticsDto(
    val rowCount: Int,
    val headerCount: Int,
    val hasData: Boolean,
    val dataType: String,
    val rowsTruncated: Boolean,
)

data class ChatHistoryResponseDto(
    val chat_id: String,
    val username: String,
    val dataDiagnostics: DataDiagnosticsDto? = null,
    val created_at: String,
    val messages: List<ChatMessageDto>,
)

data class ChatMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val created_at: String,
)

data class CreateChatRequestDto(
    val username: String,
    val selection: SelectionPayloadDto,
)

data class SelectionPayloadDto(
    val dataType: String,
    val period: String,
    val orgUnit: OrgUnitSelectionDto,
    val selectedItems: List<SelectionItemDto>,
    val programId: String? = null,
)

data class OrgUnitSelectionDto(
    val id: String,
    val displayName: String,
    val includeChildOrgUnits: Boolean,
)

data class SelectionItemDto(
    val id: String,
    val displayName: String? = null,
)

data class CreateChatResponseDto(
    val chat_id: String,
    val message: String,
    val dataDiagnostics: DataDiagnosticsDto? = null,
)

data class SendMessageRequestDto(
    val message: String,
    val options: SendMessageOptionsDto,
)

data class SendMessageOptionsDto(
    val stream: Boolean = true,
    val temperature: Double? = null,
    val model: String? = null,
)
