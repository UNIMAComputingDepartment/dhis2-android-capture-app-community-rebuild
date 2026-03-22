package org.dhis2.mobile.aichat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val username: String,
    val title: String? = null,
    val dataType: String,
    val period: String,
    val orgUnitId: String,
    val orgUnitName: String?,
    val diagnosticsJson: String?,
    val selectionJson: String,
    val createdAt: Long,
    val messageCount: Int,
    val lastMessageAt: Long?,
    val syncState: String = "SYNCED",
)
