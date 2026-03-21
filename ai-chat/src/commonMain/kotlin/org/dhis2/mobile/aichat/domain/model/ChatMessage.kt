package org.dhis2.mobile.aichat.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatRole,
    val content: String,
    val createdAt: Long,
    val syncState: SyncState = SyncState.SYNCED,
    val recommendations: List<String> = emptyList(),
)

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

