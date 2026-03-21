package org.dhis2.mobile.aichat.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.dhis2.mobile.aichat.FakeAiChatRepository
import org.dhis2.mobile.aichat.domain.usecase.GetChatMessagesUseCase
import org.dhis2.mobile.aichat.domain.usecase.SendMessageUseCase
import org.dhis2.mobile.aichat.ui.conversation.ConversationUiState
import org.dhis2.mobile.aichat.ui.conversation.ConversationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    @Test
    fun `loads content state`() = runTest {
        val repository = FakeAiChatRepository()
        val viewModel =
            ConversationViewModel(
                chatId = "chat-1",
                getChatMessagesUseCase = GetChatMessagesUseCase(repository),
                sendMessageUseCase = SendMessageUseCase(repository),
            )

        assertEquals(ConversationUiState.Loading::class, viewModel.uiState.value::class)
    }
}
