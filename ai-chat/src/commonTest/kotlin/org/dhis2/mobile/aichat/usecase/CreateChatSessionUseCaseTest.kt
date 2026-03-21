package org.dhis2.mobile.aichat.usecase

import kotlinx.coroutines.test.runTest
import org.dhis2.mobile.aichat.FakeAiChatRepository
import org.dhis2.mobile.aichat.domain.model.OrgUnitSelection
import org.dhis2.mobile.aichat.domain.model.SelectionItem
import org.dhis2.mobile.aichat.domain.model.SelectionPayload
import org.dhis2.mobile.aichat.domain.usecase.CreateChatInput
import org.dhis2.mobile.aichat.domain.usecase.CreateChatSessionUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateChatSessionUseCaseTest {
    @Test
    fun `creates session`() = runTest {
        val useCase = CreateChatSessionUseCase(FakeAiChatRepository())
        val payload =
            SelectionPayload(
                dataType = "aggregate",
                period = "THIS_YEAR",
                orgUnit = OrgUnitSelection("ou", "Org", true),
                selectedItems = listOf(SelectionItem("de")),
            )

        val result = useCase(CreateChatInput("tester", payload))

        assertTrue(result.isSuccess)
        assertEquals("chat-1", result.getOrThrow().id)
    }
}

