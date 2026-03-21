package org.dhis2.mobile.aichat.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.dhis2.mobile.aichat.domain.repository.CurrentUserProvider
import org.dhis2.mobile.aichat.domain.usecase.SyncChatsUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncAiChatWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val syncChatsUseCase: SyncChatsUseCase by inject()
    private val currentUserProvider: CurrentUserProvider by inject()

    override suspend fun doWork(): Result =
        runCatching {
            val username = currentUserProvider.username()
            syncChatsUseCase(username).getOrThrow()
            Result.success()
        }.getOrElse {
            Result.retry()
        }

    companion object {
        private const val WORK_NAME = "ai_chat_sync"

        @JvmStatic
        fun enqueue(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<SyncAiChatWorker>()
                    .setConstraints(constraints)
                    .addTag(WORK_NAME)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
