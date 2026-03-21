package org.dhis2.mobile.aichat.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.commons.domain.UseCase

class GetChatMessagesUseCase(
    private val repository: AiChatRepository,
) : UseCase<String, Flow<List<ChatMessage>>> {
    override suspend fun invoke(input: String): Result<Flow<List<ChatMessage>>> =
        runCatching {
            repository.refreshMessages(input)
            repository.observeMessages(input)
        }
}

