package org.dhis2.mobile.aichat.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.dhis2.mobile.aichat.data.local.ChatMessageDao
import org.dhis2.mobile.aichat.data.local.ChatSessionDao
import org.dhis2.mobile.aichat.data.local.entities.ChatMessageEntity
import org.dhis2.mobile.aichat.data.local.entities.ChatSessionEntity
import org.dhis2.mobile.aichat.data.remote.AiChatApiService
import org.dhis2.mobile.aichat.data.remote.dto.CreateChatRequestDto
import org.dhis2.mobile.aichat.data.remote.dto.OrgUnitSelectionDto
import org.dhis2.mobile.aichat.data.remote.dto.SelectionItemDto
import org.dhis2.mobile.aichat.data.remote.dto.SelectionPayloadDto
import org.dhis2.mobile.aichat.data.remote.dto.SendMessageOptionsDto
import org.dhis2.mobile.aichat.data.remote.dto.SendMessageRequestDto
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.model.ChatRole
import org.dhis2.mobile.aichat.domain.model.ChatSession
import org.dhis2.mobile.aichat.domain.model.DataDiagnostics
import org.dhis2.mobile.aichat.domain.model.ModelInfo
import org.dhis2.mobile.aichat.domain.model.OrgUnitSelection
import org.dhis2.mobile.aichat.domain.model.SelectionPayload
import org.dhis2.mobile.aichat.domain.model.SyncState
import org.dhis2.mobile.aichat.domain.repository.AiChatLocalRepository
import org.dhis2.mobile.aichat.domain.repository.AiChatRepository
import timber.log.Timber

class AiChatRepositoryImpl(
    private val api: AiChatApiService,
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val json: Json,
) : AiChatRepository, AiChatLocalRepository {

    override fun observeChatSessions(username: String): Flow<List<ChatSession>> =
        chatSessionDao.observeAll(username).map { entities -> entities.map(::toDomain) }

    // AiChatRepository and AiChatLocalRepository share the same observe-by-session contract.
    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        chatMessageDao.observeBySessionId(chatId).map { entities -> entities.map(::toDomain) }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun refreshChatSessions(username: String) {
        runCatching {
            val remote = api.listChats(username)
            val entities =
                remote.map {
                    ChatSessionEntity(
                        id = it.id,
                        username = it.username,
                        dataType = it.dataDiagnostics?.dataType ?: "aggregate",
                        period = "LAST_12_MONTHS",
                        orgUnitId = "",
                        orgUnitName = null,
                        diagnosticsJson = it.dataDiagnostics?.let { d -> json.encodeToString(d.toDomain()) },
                        selectionJson =
                            json.encodeToString(
                                SelectionPayload(
                                    dataType = it.dataDiagnostics?.dataType ?: "aggregate",
                                    period = "LAST_12_MONTHS",
                                    orgUnit = OrgUnitSelection("", "", true),
                                    selectedItems = emptyList(),
                                ),
                            ),
                        createdAt = parseInstant(it.created_at),
                        messageCount = it.message_count,
                        lastMessageAt = it.last_message_at?.let(::parseInstant),
                    )
                }
            chatSessionDao.upsertAll(entities)
            // Keep DB aligned with server-backed sessions only.
            chatMessageDao.deleteLocalPlaceholders()
            chatSessionDao.deleteLocalPlaceholders()
        }.onFailure { Timber.e(it, "AI chat refresh sessions failed for user=%s", username) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun refreshMessages(chatId: String) {
        if (isLocalChatId(chatId)) return
        runCatching {
            val response = api.getChatMessages(chatId)
            val entities =
                response.messages.map {
                    ChatMessageEntity(
                        id = it.id,
                        sessionId = chatId,
                        role = it.role,
                        content = it.content,
                        createdAt = parseInstant(it.created_at),
                    )
                }
            chatMessageDao.upsertAll(entities)
        }.onFailure { Timber.e(it, "AI chat refresh messages failed for chatId=%s", chatId) }
    }

    override suspend fun createChatSession(username: String, selectionPayload: SelectionPayload): ChatSession {
        val response =
            api.createChat(
                CreateChatRequestDto(
                    username = username,
                    selection =
                        SelectionPayloadDto(
                            dataType = selectionPayload.dataType,
                            period = selectionPayload.period,
                            orgUnit =
                                OrgUnitSelectionDto(
                                    id = selectionPayload.orgUnit.id,
                                    displayName = selectionPayload.orgUnit.displayName,
                                    includeChildOrgUnits = selectionPayload.orgUnit.includeChildOrgUnits,
                                ),
                            selectedItems =
                                selectionPayload.selectedItems.map { SelectionItemDto(it.id, it.displayName) },
                            programId = selectionPayload.programId,
                        ),
                ),
            )

        return ChatSession(
            id = response.chat_id,
            username = username,
            selection = selectionPayload,
            dataDiagnostics = response.dataDiagnostics?.toDomain(),
            createdAt = System.currentTimeMillis(),
            messageCount = 0,
        ).also { upsertSession(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun sendMessageStream(chatId: String, message: String): Flow<String> =
        flow {
            require(!isLocalChatId(chatId)) { "Cannot send message to a local-only chat id." }

            var accumulated = ""
            api
                .sendMessageStream(
                    chatId = chatId,
                    body =
                        SendMessageRequestDto(
                            message = message,
                            options = SendMessageOptionsDto(stream = true),
                        ),
                ).use { body ->
                    val reader = body.charStream().buffered()
                    val frameLines = mutableListOf<String>()
                    var done = false

                    suspend fun flushFrame() {
                        if (frameLines.isEmpty()) return
                        val parsed = parseSseFrame(frameLines)
                        frameLines.clear()
                        if (parsed.isDone) {
                            done = true
                            return
                        }
                        if (!parsed.token.isNullOrBlank()) {
                            accumulated += parsed.token
                            emit(accumulated)
                        }
                    }

                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) {
                            flushFrame()
                            if (done) break
                            continue
                        }
                        frameLines += line
                    }
                    if (!done) {
                        flushFrame()
                    }
                }

            refreshMessages(chatId)
        }

    override suspend fun deleteChat(chatId: String) {
        if (!isLocalChatId(chatId)) {
            api.deleteChat(chatId)
        }
        chatSessionDao.deleteById(chatId)
    }

    override suspend fun listModels(): List<ModelInfo> =
        api.listModels().models.map {
            ModelInfo(
                name = it.name,
                modifiedAt = it.modified_at,
                size = it.size,
                details = it.details,
            )
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun syncPending(username: String) {
        chatMessageDao.deleteLocalPlaceholders()
        chatSessionDao.deleteLocalPlaceholders()
        refreshChatSessions(username)
    }

    override fun observeSessions(username: String): Flow<List<ChatSession>> =
        chatSessionDao.observeAll(username).map { entities -> entities.map(::toDomain) }

    override suspend fun upsertSession(session: ChatSession) {
        chatSessionDao.upsert(session.toEntity(json))
    }

    override suspend fun upsertSessions(sessions: List<ChatSession>) {
        chatSessionDao.upsertAll(sessions.map { it.toEntity(json) })
    }

    override suspend fun upsertMessage(message: ChatMessage) {
        chatMessageDao.upsert(message.toEntity(json))
    }

    override suspend fun upsertMessages(messages: List<ChatMessage>) {
        chatMessageDao.upsertAll(messages.map { it.toEntity(json) })
    }

    override suspend fun pendingMessages(): List<ChatMessage> =
        chatMessageDao.getPendingSync().map(::toDomain)

    override suspend fun pendingSessions(): List<ChatSession> =
        chatSessionDao.getPendingSync().map(::toDomain)
}

private fun isLocalChatId(chatId: String): Boolean = chatId.startsWith("local-")

@RequiresApi(Build.VERSION_CODES.O)
private fun parseInstant(value: String): Long =
    runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

private fun ChatSession.toEntity(json: Json): ChatSessionEntity =
    ChatSessionEntity(
        id = id,
        username = username,
        dataType = selection.dataType,
        period = selection.period,
        orgUnitId = selection.orgUnit.id,
        orgUnitName = selection.orgUnit.displayName,
        diagnosticsJson = dataDiagnostics?.let { json.encodeToString(it) },
        selectionJson = json.encodeToString(selection),
        createdAt = createdAt,
        messageCount = messageCount,
        lastMessageAt = lastMessageAt,
        syncState = syncState.name,
    )

private fun ChatMessage.toEntity(json: Json): ChatMessageEntity =
    ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = role.name.lowercase(),
        content = content,
        createdAt = createdAt,
        recommendationsJson = json.encodeToString(recommendations),
        syncState = syncState.name,
    )

private fun toDomain(entity: ChatSessionEntity): ChatSession {
    val json = Json { ignoreUnknownKeys = true }
    val selection =
        runCatching { json.decodeFromString<SelectionPayload>(entity.selectionJson) }
            .getOrElse {
                SelectionPayload(
                    dataType = entity.dataType,
                    period = entity.period,
                    orgUnit = OrgUnitSelection(entity.orgUnitId, entity.orgUnitName.orEmpty(), true),
                    selectedItems = emptyList(),
                )
            }
    val diagnostics = entity.diagnosticsJson?.let { runCatching { json.decodeFromString<DataDiagnostics>(it) }.getOrNull() }
    return ChatSession(
        id = entity.id,
        username = entity.username,
        selection = selection,
        dataDiagnostics = diagnostics,
        createdAt = entity.createdAt,
        messageCount = entity.messageCount,
        lastMessageAt = entity.lastMessageAt,
        syncState = SyncState.valueOf(entity.syncState),
    )
}

private fun toDomain(entity: ChatMessageEntity): ChatMessage {
    val json = Json { ignoreUnknownKeys = true }
    val recommendations = entity.recommendationsJson?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
    return ChatMessage(
        id = entity.id,
        sessionId = entity.sessionId,
        role = ChatRole.valueOf(entity.role.uppercase()),
        content = entity.content,
        createdAt = entity.createdAt,
        syncState = SyncState.valueOf(entity.syncState),
        recommendations = recommendations,
    )
}

private fun org.dhis2.mobile.aichat.data.remote.dto.DataDiagnosticsDto.toDomain(): DataDiagnostics =
    DataDiagnostics(
        rowCount = rowCount,
        headerCount = headerCount,
        hasData = hasData,
        dataType = dataType,
        rowsTruncated = rowsTruncated,
    )

private data class ParsedSsePayload(
    val token: String?,
    val isDone: Boolean,
)

private fun parseSseFrame(lines: List<String>): ParsedSsePayload {
    val dataPayload =
        lines
            .asSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .joinToString("\n")
            .trim()

    val hasDoneEvent = lines.any { it.startsWith("event:") && it.removePrefix("event:").trim().equals("done", true) }
    if (hasDoneEvent || dataPayload == "[DONE]") {
        return ParsedSsePayload(token = null, isDone = true)
    }

    if (dataPayload.isBlank()) return ParsedSsePayload(token = null, isDone = false)
    return parseSsePayload(dataPayload)
}

private fun parseSsePayload(payload: String): ParsedSsePayload {
    if (payload == "[DONE]") return ParsedSsePayload(token = null, isDone = true)

    if (!payload.startsWith("{")) {
        val plain = payload.trim()
        if (isControlPayload(plain)) return ParsedSsePayload(token = null, isDone = false)
        return ParsedSsePayload(token = plain, isDone = false)
    }

    val jsonObject = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()
        ?: return ParsedSsePayload(token = null, isDone = false)

    val done =
        jsonObject["done"]?.jsonPrimitive?.booleanOrNull == true ||
            jsonObject["is_done"]?.jsonPrimitive?.booleanOrNull == true ||
            jsonObject.stringValue("event")?.equals("done", ignoreCase = true) == true

    if (done) return ParsedSsePayload(token = null, isDone = true)

    if (
        isControlPayload(jsonObject.stringValue("status")) ||
        isControlPayload(jsonObject.stringValue("event")) ||
        isControlPayload(jsonObject.stringValue("type"))
    ) {
        return ParsedSsePayload(token = null, isDone = false)
    }

    val token =
        jsonObject.stringValue("token")
            ?: jsonObject.stringValue("content")
            ?: jsonObject.stringValue("message")
            ?: jsonObject.stringValue("text")
            ?: jsonObject.stringValue("response")
            ?: jsonObject.stringValue("chunk")
            ?: jsonObject["delta"].jsonObjectOrNull?.stringValue("content")
            ?: jsonObject["data"].jsonObjectOrNull?.stringValue("content")
            ?: jsonObject["data"].jsonPrimitiveOrNull?.contentOrNull
            ?: jsonObject["choices"].jsonArrayOrNull?.firstOrNull()?.jsonObjectOrNull?.stringValue("text")
            ?: jsonObject["choices"].jsonArrayOrNull?.firstOrNull()?.jsonObjectOrNull?.get("delta")?.jsonObjectOrNull?.stringValue("content")

    if (isControlPayload(token)) return ParsedSsePayload(token = null, isDone = false)
    return ParsedSsePayload(token = token, isDone = false)
}

private fun isControlPayload(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    return value.trim().lowercase() in setOf(
        "flush",
        "flushing",
        "heartbeat",
        "keepalive",
        "start",
        "end",
        "token_start",
        "token_end",
        "thinking",
    )
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private val kotlinx.serialization.json.JsonElement?.jsonArrayOrNull: JsonArray?
    get() = runCatching { this?.jsonArray }.getOrNull()

private val kotlinx.serialization.json.JsonElement?.jsonObjectOrNull: JsonObject?
    get() = runCatching { this?.jsonObject }.getOrNull()

private val kotlinx.serialization.json.JsonElement?.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive
