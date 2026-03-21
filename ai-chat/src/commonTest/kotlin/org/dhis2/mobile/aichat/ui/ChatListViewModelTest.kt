package org.dhis2.mobile.aichat.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.dhis2.mobile.aichat.FakeAiChatRepository
import org.dhis2.mobile.aichat.FakeCurrentUserProvider
import org.dhis2.mobile.aichat.domain.usecase.DeleteChatSessionUseCase
import org.dhis2.mobile.aichat.domain.usecase.GetChatSessionsUseCase
import org.dhis2.mobile.aichat.ui.chatlist.ChatListUiState
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
    fun `starts in a valid list state`() = runTest {
        val repository = FakeAiChatRepository()
    @Test
                getChatSessionsUseCase = GetChatSessionsUseCase(repository),
        val viewModel =
            ChatListViewModel(
        assertTrue(viewModel.uiState.value !is ChatListUiState.Error)
                currentUserProvider = FakeCurrentUserProvider(),
                deleteChatSessionUseCase = DeleteChatSessionUseCase(repository),
            )

        assertEquals(ChatListUiState.Loading::class, viewModel.uiState.value::class)
    }
}
