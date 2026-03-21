package org.dhis2.mobile.aichat.domain.repository

import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.model.ChatSession
import org.dhis2.mobile.aichat.domain.model.ModelInfo
import org.dhis2.mobile.aichat.domain.model.SelectionPayload

interface AiChatRepository {
    fun observeChatSessions(username: String): Flow<List<ChatSession>>

    fun observeMessages(chatId: String): Flow<List<ChatMessage>>

    suspend fun refreshChatSessions(username: String)

    suspend fun refreshMessages(chatId: String)

    suspend fun createChatSession(
        username: String,
        selectionPayload: SelectionPayload,
    ): ChatSession

    fun sendMessageStream(
        chatId: String,
        message: String,
    ): Flow<String>

    suspend fun deleteChat(chatId: String)

    suspend fun listModels(): List<ModelInfo>

    suspend fun syncPending(username: String)
}
