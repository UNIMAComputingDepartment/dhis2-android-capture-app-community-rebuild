package org.dhis2.mobile.aichat.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.domain.model.ChatSession
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.commons.domain.UseCase

class GetChatSessionsUseCase(
    private val repository: AiChatRepository,
) : UseCase<String, Flow<List<ChatSession>>> {
    override suspend fun invoke(input: String): Result<Flow<List<ChatSession>>> =
        runCatching {
            repository.refreshChatSessions(input)
            repository.observeChatSessions(input)
        }
}

