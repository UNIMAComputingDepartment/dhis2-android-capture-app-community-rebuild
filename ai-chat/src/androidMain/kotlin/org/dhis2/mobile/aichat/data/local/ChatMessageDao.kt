package org.dhis2.mobile.aichat.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.data.local.entities.ChatMessageEntity

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySessionId(sessionId: String): Flow<List<ChatMessageEntity>>

    @Upsert
    suspend fun upsert(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM ai_chat_messages WHERE syncState != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getPendingSync(): List<ChatMessageEntity>

    @Query("UPDATE ai_chat_messages SET sessionId = :newSessionId WHERE sessionId = :oldSessionId")
    suspend fun reassignSessionId(oldSessionId: String, newSessionId: String): Int

    @Query("DELETE FROM ai_chat_messages WHERE id LIKE 'local-%' OR sessionId LIKE 'local-%'")
    suspend fun deleteLocalPlaceholders()
}
