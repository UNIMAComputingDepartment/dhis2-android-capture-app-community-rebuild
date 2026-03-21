package org.dhis2.mobile.aichat.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.data.local.entities.ChatSessionEntity

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM ai_chat_sessions WHERE username = :username ORDER BY COALESCE(lastMessageAt, createdAt) DESC")
    fun observeAll(username: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatSessionEntity?

    @Upsert
    suspend fun upsert(session: ChatSessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<ChatSessionEntity>)

    @Query("DELETE FROM ai_chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM ai_chat_sessions WHERE syncState != 'SYNCED'")
    suspend fun getPendingSync(): List<ChatSessionEntity>

    @Query("DELETE FROM ai_chat_sessions WHERE id LIKE 'local-%'")
    suspend fun deleteLocalPlaceholders()
}
