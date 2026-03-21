package org.dhis2.mobile.aichat.domain.usecase

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.commons.domain.UseCase

class GetChatMessagesUseCase(
    private val repository: AiChatRepository,
) : UseCase<String, Flow<List<ChatMessage>>> {
    override suspend fun invoke(input: String): Result<Flow<List<ChatMessage>>> =
        runCatching {
            channelFlow {
                val observeJob =
                    launch {
                        repository.observeMessages(input).collect { send(it) }
                    }

                // Refresh runs in parallel so cached Room messages appear immediately.
                launch {
                    runCatching { repository.refreshMessages(input) }
                }

                awaitClose { observeJob.cancel() }
            }
        }
}
