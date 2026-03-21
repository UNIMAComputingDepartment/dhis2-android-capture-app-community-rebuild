package org.dhis2.mobile.aichat.ui.conversation

import org.dhis2.mobile.aichat.domain.model.ChatMessage

sealed interface ConversationUiState {
    data object Loading : ConversationUiState

    data class Content(
        val messages: List<ChatMessage>,
        val sending: Boolean = false,
        val input: String = "",
        val streamingContent: String = "",
        val pendingUserMessage: String? = null,
    ) : ConversationUiState

    data class Error(
        val message: String,
    ) : ConversationUiState
}
