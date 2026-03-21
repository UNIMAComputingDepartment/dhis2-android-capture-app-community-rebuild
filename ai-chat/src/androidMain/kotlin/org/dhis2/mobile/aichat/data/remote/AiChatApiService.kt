package org.dhis2.mobile.aichat.data.remote

import okhttp3.ResponseBody
import org.dhis2.mobile.aichat.data.remote.dto.ChatHistoryResponseDto
import org.dhis2.mobile.aichat.data.remote.dto.ChatSessionSummaryDto
import org.dhis2.mobile.aichat.data.remote.dto.CreateChatRequestDto
import org.dhis2.mobile.aichat.data.remote.dto.CreateChatResponseDto
import org.dhis2.mobile.aichat.data.remote.dto.HealthResponse
import org.dhis2.mobile.aichat.data.remote.dto.ModelsResponse
import org.dhis2.mobile.aichat.data.remote.dto.SendMessageRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AiChatApiService {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("api/models")
    suspend fun listModels(): ModelsResponse

    @GET("api/chats")
    suspend fun listChats(
        @Query("username") username: String,
    ): List<ChatSessionSummaryDto>

    @GET("api/chats/{chatId}/messages")
    suspend fun getChatMessages(
        @Path("chatId") chatId: String,
    ): ChatHistoryResponseDto

    @POST("api/chats")
    suspend fun createChat(
        @Body body: CreateChatRequestDto,
    ): CreateChatResponseDto

    @Streaming
    @POST("api/chats/{chatId}/messages")
    suspend fun sendMessageStream(
        @Path("chatId") chatId: String,
        @Body body: SendMessageRequestDto,
    ): ResponseBody

    @DELETE("api/chats/{chatId}")
    suspend fun deleteChat(
        @Path("chatId") chatId: String,
    )
}
