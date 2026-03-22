package org.dhis2.mobile.aichat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.dhis2.mobile.aichat.data.local.entities.ChatMessageEntity
import org.dhis2.mobile.aichat.data.local.entities.ChatSessionEntity

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AiChatDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao

    abstract fun chatMessageDao(): ChatMessageDao
}
