package org.dhis2.mobile.aichat.domain.repository

import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.model.ChatSession

interface AiChatLocalRepository {
    fun observeSessions(username: String): Flow<List<ChatSession>>

    fun observeMessages(chatId: String): Flow<List<ChatMessage>>

    suspend fun upsertSession(session: ChatSession)

    suspend fun upsertSessions(sessions: List<ChatSession>)

    suspend fun upsertMessage(message: ChatMessage)

    suspend fun upsertMessages(messages: List<ChatMessage>)

    suspend fun pendingMessages(): List<ChatMessage>

    suspend fun pendingSessions(): List<ChatSession>
}

