package org.dhis2.mobile.aichat.domain.usecase

import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.commons.domain.UseCase

class SyncChatsUseCase(
    private val repository: AiChatRepository,
) : UseCase<String, Unit> {
    override suspend fun invoke(input: String): Result<Unit> = runCatching {
        repository.syncPending(input)
    }
}

