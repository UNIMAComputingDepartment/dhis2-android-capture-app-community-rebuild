package org.dhis2.mobile.aichat.domain.model

data class ChatSession(
    val id: String,
    val username: String,
    val selection: SelectionPayload,
    val dataDiagnostics: DataDiagnostics? = null,
    val createdAt: Long,
    val messageCount: Int,
    val lastMessageAt: Long? = null,
    val syncState: SyncState = SyncState.SYNCED,
)

enum class SyncState {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DOWNLOAD,
}

