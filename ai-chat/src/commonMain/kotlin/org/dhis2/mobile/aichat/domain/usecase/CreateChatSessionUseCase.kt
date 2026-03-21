package org.dhis2.mobile.aichat.domain.usecase

import org.dhis2.mobile.aichat.domain.model.ChatSession
import org.dhis2.mobile.aichat.domain.model.SelectionPayload
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.commons.domain.UseCase

data class CreateChatInput(
    val username: String,
    val selectionPayload: SelectionPayload,
)

class CreateChatSessionUseCase(
    private val repository: AiChatRepository,
) : UseCase<CreateChatInput, ChatSession> {
    override suspend fun invoke(input: CreateChatInput): Result<ChatSession> =
        runCatching {
            repository.createChatSession(input.username, input.selectionPayload)
        }
}

