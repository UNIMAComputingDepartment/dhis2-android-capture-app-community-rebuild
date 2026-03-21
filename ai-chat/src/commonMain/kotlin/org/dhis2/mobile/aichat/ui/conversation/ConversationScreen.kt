package org.dhis2.mobile.aichat.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.model.ChatRole
import org.dhis2.mobile.aichat.ui.components.CenteredLoadingState
import org.dhis2.mobile.aichat.ui.components.ChatBubble
import org.dhis2.mobile.aichat.ui.components.StreamingIndicator

@Composable
fun ConversationScreen(
    uiState: ConversationUiState,
    onInputChanged: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    when (uiState) {
        ConversationUiState.Loading -> CenteredLoadingState(message = "Loading conversation...")
        is ConversationUiState.Error -> Text(
            uiState.message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        is ConversationUiState.Content -> {
            val listState = rememberLazyListState()
            val showStreamingBubble = uiState.sending || uiState.streamingContent.isNotBlank()
            val uiMessageCount =
                uiState.messages.size +
                    (if (!uiState.pendingUserMessage.isNullOrBlank()) 1 else 0) +
                    (if (showStreamingBubble) 1 else 0)

            LaunchedEffect(uiMessageCount, uiState.pendingUserMessage, uiState.streamingContent, uiState.sending) {
                if (uiMessageCount > 0) {
                    listState.animateScrollToItem(uiMessageCount - 1)
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages) { message ->
                        val alignEnd = message.role == ChatRole.USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
                        ) {
                            ChatBubble(message = message)
                        }
                    }

                    if (!uiState.pendingUserMessage.isNullOrBlank()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                ChatBubble(
                                    message =
                                        ChatMessage(
                                            id = "pending-user",
                                            sessionId = "pending",
                                            role = ChatRole.USER,
                                            content = uiState.pendingUserMessage,
                                            createdAt = System.currentTimeMillis(),
                                        ),
                                )
                            }
                        }
                    }

                    if (showStreamingBubble) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (uiState.streamingContent.isNotBlank()) {
                                    ChatBubble(
                                        message =
                                            ChatMessage(
                                                id = "streaming",
                                                sessionId = "streaming",
                                                role = ChatRole.ASSISTANT,
                                                content = uiState.streamingContent,
                                                createdAt = System.currentTimeMillis(),
                                            ),
                                        isStreaming = uiState.sending,
                                    )
                                } else {
                                    Column {
                                        ChatBubble(
                                            message =
                                                ChatMessage(
                                                    id = "typing",
                                                    sessionId = "typing",
                                                    role = ChatRole.ASSISTANT,
                                                    content = "Thinking...",
                                                    createdAt = System.currentTimeMillis(),
                                                ),
                                            isStreaming = true,
                                        )
                                        StreamingIndicator(modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = onInputChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about your data") },
                    )
                    IconButton(onClick = onSendClick, enabled = uiState.input.isNotBlank() && !uiState.sending) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    }
}
