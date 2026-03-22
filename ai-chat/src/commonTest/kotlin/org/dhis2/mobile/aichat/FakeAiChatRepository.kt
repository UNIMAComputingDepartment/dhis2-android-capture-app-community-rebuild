package org.dhis2.mobile.aichat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.model.ChatRole
import org.dhis2.mobile.aichat.domain.model.ChatSession
import org.dhis2.mobile.aichat.domain.model.ModelInfo
import org.dhis2.mobile.aichat.domain.model.SelectionPayload
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository

class FakeAiChatRepository : AiChatRepository {
    val sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    override fun observeChatSessions(username: String): Flow<List<ChatSession>> = sessions

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = messages

    override suspend fun refreshChatSessions(username: String) = Unit

    override suspend fun refreshMessages(chatId: String) = Unit

    override suspend fun createChatSession(username: String, selectionPayload: SelectionPayload): ChatSession {
        val chat =
            ChatSession(
                id = "chat-1",
                username = username,
                selection = selectionPayload,
                createdAt = 1L,
                messageCount = 0,
            )
        sessions.value = listOf(chat)
        return chat
    }

    override fun sendMessageStream(chatId: String, message: String): Flow<String> {
        val chatMessage =
            ChatMessage(
                id = "msg-1",
                sessionId = chatId,
                role = ChatRole.ASSISTANT,
                content = "ok",
                createdAt = 1L,
            )
        messages.value = listOf(chatMessage)
        return flowOf(chatMessage.content)
    }

    override suspend fun deleteChat(chatId: String) {
        sessions.value = sessions.value.filterNot { it.id == chatId }
    }

    override suspend fun listModels(): List<ModelInfo> = emptyList()

    override suspend fun syncPending(username: String) = Unit
}
