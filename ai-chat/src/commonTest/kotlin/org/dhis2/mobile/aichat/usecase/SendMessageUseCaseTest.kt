package org.dhis2.mobile.aichat.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.dhis2.mobile.aichat.domain.model.ChatRole
import org.dhis2.mobile.aichat.domain.usecase.SendMessageInput
import org.dhis2.mobile.aichat.domain.usecase.SendMessageUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

class SendMessageUseCaseTest {
    @Test
    fun `returns assistant stream content`() = runTest {
        val useCase = SendMessageUseCase(FakeAiChatRepository())

        val result = useCase(SendMessageInput("chat-1", "hello")).getOrThrow().first()

        assertEquals("ok", result)
    }
}
