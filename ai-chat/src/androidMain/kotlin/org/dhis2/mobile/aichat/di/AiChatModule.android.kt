package org.dhis2.mobile.aichat.di

import androidx.room.Room
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.dhis2.mobile.aichat.BuildConfig
import org.dhis2.mobile.aichat.data.local.AiChatDatabase
import org.dhis2.mobile.aichat.data.remote.AiChatApiService
import org.dhis2.mobile.aichat.data.repository.AiChatRepositoryImpl
import org.dhis2.mobile.aichat.data.repository.D2CurrentUserProvider
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import org.dhis2.mobile.aichat.domain.repository.CurrentUserProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

actual val platformAiChatModule: Module =
    module {
        single { AiChatConfig(baseUrl = sanitizeBaseUrl(BuildConfig.AI_CHAT_BASE_URL)) }
        single {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        single { provideAiChatOkHttpClient() }
        single {
            Retrofit
                .Builder()
                .baseUrl(get<AiChatConfig>().baseUrl)
                .client(get())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        single { get<Retrofit>().create(AiChatApiService::class.java) }
        single {
            Room
                .databaseBuilder(androidContext(), AiChatDatabase::class.java, "ai_chat.db")
                .fallbackToDestructiveMigration()
                .build()
        }
        single { get<AiChatDatabase>().chatSessionDao() }
        single { get<AiChatDatabase>().chatMessageDao() }
        single<AiChatRepository> { AiChatRepositoryImpl(get(), get(), get(), get()) }
        single<CurrentUserProvider> { D2CurrentUserProvider(get()) }
    }

private fun provideAiChatOkHttpClient(): OkHttpClient {
    val loggingInterceptor =
        HttpLoggingInterceptor { message ->
            Timber.tag("AiChatHttp").d(message)
        }.apply {
            // BODY logging can interfere with long-lived SSE streams.
            level = HttpLoggingInterceptor.Level.HEADERS
        }

    return OkHttpClient
        .Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        // Keep SSE streams alive for long model responses.
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(loggingInterceptor)
        .build()
}

private fun sanitizeBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    val withScheme =
        when {
            trimmed.startsWith("ttps://") -> "h$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }

    val normalized = if (withScheme.endsWith('/')) withScheme else "$withScheme/"

    return runCatching {
        normalized.toHttpUrl()
        normalized
    }.getOrElse {
        val fallback = BuildConfig.AI_CHAT_BASE_URL_LOCALHOST
        Timber.e(it, "Invalid AI Chat base URL '%s'. Falling back to localhost.", rawUrl)
        if (fallback.endsWith('/')) fallback else "$fallback/"
    }
}
