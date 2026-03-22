package org.dhis2.mobile.aichat.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        updateContent { copy(input = value) }
    }

    fun send() {
        val state = _uiState.value as? ConversationUiState.Content ?: return
        val messageToSend = state.input.trim()
        if (messageToSend.isBlank() || state.sending) return

        updateContent {
            copy(
                sending = true,
                input = "",
                streamingContent = "",
                pendingUserMessage = messageToSend,
            )
        }

        viewModelScope.launch {
            sendMessageUseCase(SendMessageInput(chatId = chatId, message = messageToSend)).fold(
                onSuccess = { stream ->
                    runCatching {
                        stream.collect { chunk ->
                            updateContent {
                                val nextStreaming =
                                    when {
                                        chunk.isEmpty() -> streamingContent
                                        chunk.startsWith(streamingContent) -> chunk
                                        else -> streamingContent + chunk
                                    }
                                copy(
                                    sending = true,
                                    streamingContent = nextStreaming,
                                    pendingUserMessage = pendingUserMessage ?: messageToSend,
                                )
                            }
                        }
                    }.onSuccess {
                        updateContent {
                            copy(
                                sending = false,
                                streamingContent = "",
                                pendingUserMessage = null,
                            )
                        }
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        updateContent {
                            copy(
                                sending = false,
                                streamingContent = "",
                                pendingUserMessage = null,
                            )
                        }
                    }
                },
                onFailure = {
                    updateContent {
                        copy(
                            sending = false,
                            streamingContent = "",
                            pendingUserMessage = null,
                        )
                    }
                },
            )
        }
    }

    fun manualSync() {
        observeMessages()
    }

    private fun observeMessages() {
        observeMessagesJob?.cancel()
        observeMessagesJob =
            viewModelScope.launch {
                getChatMessagesUseCase(chatId).fold(
                    onSuccess = { flow ->
                        flow.collect { messages ->
                            _uiState.update { current ->
                                val currentContent = current as? ConversationUiState.Content
                                val visibleMessages = messages.filterNot { it.role == ChatRole.SYSTEM }
                                val streamedText = currentContent?.streamingContent.orEmpty()
                                val hasPersistedAssistant =
                                    streamedText.isNotBlank() &&
                                        visibleMessages.lastOrNull { it.role == ChatRole.ASSISTANT }
                                            ?.let { normalizeForComparison(it.content).contains(normalizeForComparison(streamedText)) }
                                            ?: false

                                ConversationUiState.Content(
                                    messages = visibleMessages,
                                    sending = currentContent?.sending ?: false,
                                    input = currentContent?.input.orEmpty(),
                                    streamingContent = if (hasPersistedAssistant) "" else streamedText,
                                    pendingUserMessage = currentContent?.pendingUserMessage,
                                )
                            }
                        }
                    },
                    onFailure = {
                        _uiState.value = ConversationUiState.Error(it.message ?: "Unable to load conversation")
                    },
                )
            }
    }

    private inline fun updateContent(
        transform: ConversationUiState.Content.() -> ConversationUiState.Content,
    ) {
        _uiState.update { state ->
            val content = state as? ConversationUiState.Content ?: return@update state
            transform(content)
        }
    }
}

private fun normalizeForComparison(value: String): String =
    value.replace("\r\n", "\n").replace(Regex("\\s+"), " ").trim()
