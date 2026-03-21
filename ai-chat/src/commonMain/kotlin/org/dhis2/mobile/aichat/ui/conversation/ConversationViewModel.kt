package org.dhis2.mobile.aichat.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.dhis2.mobile.aichat.domain.model.ChatRole
import org.dhis2.mobile.aichat.domain.usecase.GetChatMessagesUseCase
import org.dhis2.mobile.aichat.domain.usecase.SendMessageInput
import org.dhis2.mobile.aichat.domain.usecase.SendMessageUseCase

class ConversationViewModel(
    private val chatId: String,
    private val getChatMessagesUseCase: GetChatMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var observeMessagesJob: Job? = null

    init {
        observeMessages()
    }

    fun onInputChanged(value: String) {
        val state = _uiState.value as? ConversationUiState.Content ?: return
        _uiState.value = state.copy(input = value)
    }

    fun send() {
        val state = _uiState.value as? ConversationUiState.Content ?: return
        val messageToSend = state.input.trim()
        if (messageToSend.isBlank() || state.sending) return

        _uiState.value =
            state.copy(
                sending = true,
                input = "",
                streamingContent = "",
                pendingUserMessage = messageToSend,
            )

        viewModelScope.launch {
            sendMessageUseCase(SendMessageInput(chatId = chatId, message = messageToSend)).fold(
                onSuccess = { stream ->
                    stream.collect { chunk ->
                        val current = _uiState.value as? ConversationUiState.Content ?: return@collect
                        _uiState.value = current.copy(streamingContent = chunk, sending = true)
                    }
                    val current = _uiState.value as? ConversationUiState.Content ?: return@fold
                    _uiState.value =
                        current.copy(
                            sending = false,
                            streamingContent = "",
                            pendingUserMessage = null,
                        )
                },
                onFailure = {
                    val current = _uiState.value as? ConversationUiState.Content ?: return@fold
                    _uiState.value =
                        current.copy(
                            sending = false,
                            pendingUserMessage = null,
                        )
                },
            )
        }
    }

    private fun observeMessages() {
        observeMessagesJob?.cancel()
        observeMessagesJob =
            viewModelScope.launch {
                getChatMessagesUseCase(chatId).fold(
                    onSuccess = { flow ->
                        flow.collect { messages ->
                            val current = _uiState.value as? ConversationUiState.Content
                            _uiState.value =
                                ConversationUiState.Content(
                                    messages = messages.filterNot { it.role == ChatRole.SYSTEM },
                                    sending = current?.sending ?: false,
                                    input = current?.input.orEmpty(),
                                    streamingContent = current?.streamingContent.orEmpty(),
                                    pendingUserMessage = current?.pendingUserMessage,
                                )
                        }
                    },
                    onFailure = {
                        _uiState.value = ConversationUiState.Error(it.message ?: "Unable to load conversation")
                    },
                )
            }
    }
}
