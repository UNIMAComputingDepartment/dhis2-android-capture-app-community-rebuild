package org.dhis2.mobile.aichat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.dhis2.mobile.aichat.domain.repository.CurrentUserProvider
import org.dhis2.mobile.aichat.domain.usecase.DeleteChatSessionUseCase
import org.dhis2.mobile.aichat.domain.usecase.GetChatSessionsUseCase

class ChatListViewModel(
    private val getChatSessionsUseCase: GetChatSessionsUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val deleteChatSessionUseCase: DeleteChatSessionUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        observeJob?.cancel()
        observeJob =
            viewModelScope.launch {
                _uiState.value = ChatListUiState.Loading
                val username = currentUserProvider.username()
                getChatSessionsUseCase(username)
                    .fold(
                        onSuccess = { flow ->
                            flow.collect { sessions ->
                                _uiState.value =
                                    if (sessions.isEmpty()) {
                                        ChatListUiState.Empty
                                    } else {
                                        ChatListUiState.Content(sessions)
                                    }
                            }
                        },
                        onFailure = {
                            _uiState.value = ChatListUiState.Error(it.message ?: "Unable to load chats")
                        },
                    )
            }
    }

    fun deleteChat(chatId: String) {
        val current = _uiState.value as? ChatListUiState.Content ?: return
        val updatedSessions = current.sessions.filterNot { it.id == chatId }
        _uiState.value = if (updatedSessions.isEmpty()) ChatListUiState.Empty else ChatListUiState.Content(updatedSessions)

        viewModelScope.launch {
            deleteChatSessionUseCase(chatId).onFailure {
                _uiState.value = ChatListUiState.Error(it.message ?: "Unable to delete chat")
                refresh()
            }
        }
    }
}
