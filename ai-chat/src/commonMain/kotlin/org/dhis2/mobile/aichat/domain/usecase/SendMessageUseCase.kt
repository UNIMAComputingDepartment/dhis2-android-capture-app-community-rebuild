package org.dhis2.mobile.aichat.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.commons.domain.UseCase

data class SendMessageInput(
    val chatId: String,
    val message: String,
)

class SendMessageUseCase(
    private val repository: AiChatRepository,
) : UseCase<SendMessageInput, Flow<String>> {
    override suspend fun invoke(input: SendMessageInput): Result<Flow<String>> =
        runCatching {
            repository.sendMessageStream(input.chatId, input.message)
        }
}
