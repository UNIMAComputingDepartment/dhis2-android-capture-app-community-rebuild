package org.dhis2.mobile.aichat.usecase

import kotlinx.coroutines.test.runTest
import org.dhis2.mobile.aichat.FakeAiChatRepository
import org.dhis2.mobile.aichat.domain.usecase.GetChatSessionsUseCase
import kotlin.test.Test
import kotlin.test.assertTrue

class GetChatSessionsUseCaseTest {
    @Test
    fun `returns success flow`() = runTest {
        val useCase = GetChatSessionsUseCase(FakeAiChatRepository())

        val result = useCase("tester")

        assertTrue(result.isSuccess)
    }
}

