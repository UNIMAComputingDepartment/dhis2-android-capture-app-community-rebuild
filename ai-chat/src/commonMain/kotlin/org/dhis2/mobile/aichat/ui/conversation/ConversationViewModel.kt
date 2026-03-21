package org.dhis2.mobile.aichat.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.mobile.aichat.domain.model.ChatMessage
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
                                        streamingContent.isBlank() -> chunk
                                        chunk.startsWith(streamingContent) -> chunk
                                        else -> appendWithNaturalSpacing(streamingContent, chunk)
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
                                pendingUserMessage = null,
                            )
                        }
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        updateContent {
                            copy(
                                sending = false,
                                pendingUserMessage = null,
                            )
                        }
                    }
                },
                onFailure = {
                    updateContent {
                        copy(
                            sending = false,
                            pendingUserMessage = null,
                        )
                    }
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
                            _uiState.update { current ->
                                val currentContent = current as? ConversationUiState.Content
                                val visibleMessages =
                                    mergeConsecutiveAssistantMessages(
                                        messages.filterNot { it.role == ChatRole.SYSTEM },
                                    )
                                val streamedText = currentContent?.streamingContent.orEmpty()
                                val hasPersistedAssistant =
                                    streamedText.isNotBlank() &&
                                        visibleMessages.any { it.role == ChatRole.ASSISTANT && it.content.contains(streamedText) }

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

private fun mergeConsecutiveAssistantMessages(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.isEmpty()) return emptyList()

    val merged = mutableListOf<ChatMessage>()
    messages.forEach { message ->
        val last = merged.lastOrNull()
        val shouldMerge = last?.role == ChatRole.ASSISTANT && message.role == ChatRole.ASSISTANT

        if (!shouldMerge) {
            merged += message
        } else {
            merged[merged.lastIndex] =
                last.copy(
                    content = appendWithNaturalSpacing(last.content, message.content),
                    recommendations = (last.recommendations + message.recommendations).distinct(),
                )
        }
    }
    return merged
}

private fun appendWithNaturalSpacing(existing: String, incoming: String): String {
    if (existing.isEmpty()) return incoming
    if (incoming.isEmpty()) return existing

    val left = existing.last()
    val right = incoming.first()
    val needsSpace =
        !left.isWhitespace() &&
            !right.isWhitespace() &&
            right !in setOf('.', ',', ';', ':', '!', '?', ')', ']', '}') &&
            left !in setOf('(', '[', '{', '-', '/', '\n')

    return if (needsSpace) "$existing $incoming" else existing + incoming
}
