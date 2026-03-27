package org.dhis2.mobile.aichat.data

import android.content.Context
import androidx.core.content.FileProvider
import org.dhis2.mobile.aichat.R

class AiChatProvider : FileProvider(R.xml.ai_chat_file_paths) {
    companion object {
        fun authority(context: Context): String = "${context.packageName}.aichat.data.AiChatFileProvider"
    }
}

