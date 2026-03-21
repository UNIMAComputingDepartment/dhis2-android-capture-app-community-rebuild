package org.dhis2.mobile.aichat.ui.chatlist

import org.dhis2.mobile.aichat.domain.model.ChatSession

sealed interface ChatListUiState {
    data object Loading : ChatListUiState

    data object Empty : ChatListUiState

    data class Content(
        val sessions: List<ChatSession>,
    ) : ChatListUiState

    data class Error(
        val message: String,
    ) : ChatListUiState
}

